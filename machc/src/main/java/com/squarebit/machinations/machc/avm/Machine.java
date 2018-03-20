package com.squarebit.machinations.machc.avm;

import com.squarebit.machinations.machc.avm.exceptions.MachineException;
import com.squarebit.machinations.machc.avm.instructions.*;
import com.squarebit.machinations.machc.avm.runtime.TObject;
import com.squarebit.machinations.machc.avm.runtime.TObjectBase;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

public final class Machine {

    private ModuleInfo moduleInfo;

    //////////////////////////////////////////
    // Machine thread dispatcher and running state
    //
    private Dispatcher dispatcher;
    private boolean isRunning = false;
    private final ConcurrentLinkedQueue<Runnable> machineThreadTasks = new ConcurrentLinkedQueue<>();

    //////////////////////////////////////////
    // Call stack and data stack.
    private MethodFrame activeMethodFrame = null;
    private Frame activeFrame = null;
    private Stack<TObject> dataStack;


    // Expression machine
    private ExpressionMachine expressionMachine;

    // Native method cache.
    private NativeMethodCache nativeMethodCache;

    /**
     * Initializes a new machine instance.
     */
    public Machine(ModuleInfo moduleInfo) {
        this.moduleInfo = moduleInfo;
        this.dispatcher = Dispatcher.createSingleThreadDispatcher("@avm_main");
        this.activeFrame = null;
        this.dataStack = new Stack<>();
        this.expressionMachine = new ExpressionMachine(this);
        this.nativeMethodCache = new NativeMethodCache();
    }

    /**
     * Starts the machine and its internal loop.
     */
    public void start() {
        isRunning = true;
        dispatcher.dispatch(this::runLoopOnce);
    }

    /**
     * Stops the machine and its internal loop.
     */
    public void shutdown() {
        isRunning = false;
        dispatcher.shutdown();
    }

    /**
     *
     * @param moduleInfo
     */
    public void loadModule(ModuleInfo moduleInfo) {
        this.moduleInfo = moduleInfo;
    }

    /**
     * Find type type info.
     *
     * @param name the name
     * @return the type info
     */
    public TypeInfo findType(String name) {
        return this.moduleInfo.findType(name);
    }

    /**
     * Initializes a new instance of given type.
     *
     * @param typeInfo the {@link TypeInfo} of given type
     * @return a {@link CompletableFuture} object holding invocation result
     */
    public CompletableFuture<TObject> newInstance(TypeInfo typeInfo) {
        CompletableFuture<TObject> result = new CompletableFuture<>();
        executeOnMachineThread(() -> {
            try {
                TObject instance = typeInfo.allocateInstance();
                CompletableFuture<TObject> returnFuture =
                        machInvokeOnMachineThread(typeInfo.getInternalInstanceConstructor(), instance);
                returnFuture.whenComplete((v, ex) -> {
                    if (ex != null)
                        result.completeExceptionally(ex);
                    else
                        result.complete(instance);
                });
            }
            catch (Exception ex) {
                result.completeExceptionally(ex);
            }
        });

        return result;
    }

    /**
     * Execute one execution loop, which executes one instruction at a time.
     */
    private void runLoopOnce() {
        if (!isRunning)
            return;

        // Execute tasks on machine thread.
        while (!machineThreadTasks.isEmpty()) {
            Runnable task = machineThreadTasks.poll();
            task.run();
        }

        executeNextInstruction();

        // Repeat.
        dispatcher.dispatch(this::runLoopOnce);
    }

    /**
     * Execute next instruction in the call stack/active frame.
     */
    private void executeNextInstruction() {
        try {
            Frame frame = this.activeFrame;
            boolean canExecute = false;
            boolean waitForNativeMethodReturn = false;

            while (frame != null && !canExecute && !waitForNativeMethodReturn) {
                if (!canExecuteNextInstruction(frame)) {
                    NativeMethodFrame nativeMethodFrame = frame.getActiveNativeMethodFrame();

                    if (nativeMethodFrame == null) {
                        exitFrame(frame);
                        frame = frame.getCaller();
                        this.activeFrame = frame;
                    }
                    else {
                        CompletableFuture<TObject> returnFuture = nativeMethodFrame.getReturnFuture();

                        if (returnFuture.isDone()) {
                            frame.setActiveNativeMethodFrame(nativeMethodFrame.getCaller());
                        }
                        else
                            waitForNativeMethodReturn = true;
                    }
                }
                else
                    canExecute = true;
            }

            if (canExecute) {
                InstructionFrame blockFrame = (InstructionFrame)frame;
                Instruction instruction = blockFrame.next();
                executeInstruction(instruction);
            }
        }
        catch (Exception ex) {
            // Something happens.
            panicReturn(ex);
        }
    }

    /**
     * Determines if we can execute next instruction in given frame.
     * @param frame the frame.
     * @return true or false
     */
    private boolean canExecuteNextInstruction(Frame frame) {
        if (frame instanceof InstructionFrame) {
            InstructionFrame instructionFrame = (InstructionFrame)frame;
            return instructionFrame.hasNext();
        }

        return false;
    }

    /**
     * Prepares to enter a new frame.
     * @param frame the new frame
     */
    private void enterFrame(Frame frame) {
        for (int i = 0; i < frame.getLocalVariableCount(); i++)
            dataStack.push(null);
    }

    /**
     * Prepare to exit an existing frame.
     * @param frame the exiting frame.
     */
    private void exitFrame(Frame frame) {
        for (int i = 0; i < frame.getLocalVariableCount(); i++)
            dataStack.pop();

        if (frame instanceof MethodFrame) {
            MethodFrame exitingFrame = (MethodFrame)frame;

            // Move active method frame to nearest method frame.
            frame = frame.getCaller();
            while (frame != null && !(frame instanceof MethodFrame))
                frame = frame.getCaller();

            if (frame != null)
                this.activeMethodFrame = (MethodFrame)frame;

            // Result is resolve finally.
            exitingFrame.getReturnFuture().complete(exitingFrame.getReturnValue());
        }
    }

    /**
     * Execute an instruction.
     * @param instruction instruction to execute
     */
    private void executeInstruction(Instruction instruction) {
        if (instruction instanceof Evaluate)
            executeEvaluate((Evaluate)instruction);
        else if (instruction instanceof PutField)
            executePutField((PutField)instruction);
        else if (instruction instanceof LoadField)
            executeLoadField((LoadField)instruction);
        else if (instruction instanceof Invoke)
            executeInvoke((Invoke)instruction);
        else if (instruction instanceof Return)
            executeReturn((Return)instruction);
        else if (instruction instanceof New)
            executeNew((New)instruction);
        else
            throw new RuntimeException("Unimplemented instruction");
    }

    /**
     * Invoke given method. Must be run by machine thread.
     * @param method
     * @param instance
     * @param args
     * @return
     */
    CompletableFuture<TObject> machInvokeOnMachineThread(MethodInfo method, TObject instance, TObject ... args) {
        MethodFrame methodFrame = pushMethodFrame(method);
        setLocalVariable(0, instance);
        for (int i = 0; i < args.length; i++)
            setLocalVariable(i + 1, args[i]);

        // Push the method first block.
        pushInstructionBlockFrame(method.getInstructionBlock());

        return methodFrame.getReturnFuture();
    }

    /**
     * Push a new frame for a method.
     * @param methodInfo the method
     * @return the method frame.
     */
    private MethodFrame pushMethodFrame(MethodInfo methodInfo) {
        MethodFrame methodFrame = new MethodFrame(this.activeFrame, dataStack.size(), methodInfo);
        enterFrame(methodFrame);

        this.activeMethodFrame = methodFrame;
        this.activeFrame = methodFrame;
        return methodFrame;
    }

    /**
     * Push a new frame for an instruction block
     * @param instructionBlock the instruction block
     * @return the instruction block frame
     */
    private InstructionBlockFrame pushInstructionBlockFrame(InstructionBlock instructionBlock) {
        InstructionBlockFrame blockFrame = new InstructionBlockFrame(this.activeFrame, instructionBlock);
        enterFrame(blockFrame);
        this.activeFrame = blockFrame;
        return blockFrame;
    }

    /**
     * Unrolls everything in case of errors.
     * @param exception the cause of the error
     */
    private void panicReturn(Exception exception) {
        Frame frame = this.activeFrame;

        while (frame != null) {
            if (frame instanceof MethodFrame) {
                MethodFrame methodFrame = (MethodFrame)frame;
                methodFrame.getReturnFuture().completeExceptionally(new MachineException(exception));
            }

            frame = frame.getCaller();
        }
    }

    /**
     * Sets a local variable on the stack.
     * @param index variable index.
     * @param value value to set
     */
    void setLocalVariable(int index, TObject value) {
        dataStack.set(this.activeMethodFrame.getOffset() + index, value);
    }

    /**
     * Gets a local variable on the stack.
     * @param index the variable index.
     * @return variable value.
     */
    TObject getLocalVariable(int index) {
        return dataStack.get(this.activeMethodFrame.getOffset() + index);
    }

    /**
     * Execute a task on machine thread.
     * @param runnable
     */
    private void executeOnMachineThread(Runnable runnable) {
        machineThreadTasks.add(runnable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Instructions execution

    private void executeEvaluate(Evaluate instruction) {
        TObject result = expressionMachine.evaluate(instruction.getExpression());
        setLocalVariable(instruction.getTo().getIndex(), result);
    }

    private void executePutField(PutField instruction) {
        TObject value = getLocalVariable(instruction.getFrom().getIndex());

        TObject instance = getLocalVariable(instruction.getInstance().getIndex());
        if (instance instanceof TObjectBase) {
            TObjectBase objectBase = (TObjectBase)instance;
            objectBase.setField(instruction.getFieldInfo().getIndex(), value);
        }
        else
            throw new RuntimeException("The given field does not belong to given type.");
    }

    private void executeLoadField(LoadField instruction) {
        TObject owner = getLocalVariable(instruction.getInstance().getIndex());
        if (owner instanceof TObjectBase) {
            TObjectBase objectBase = (TObjectBase)owner;
            TObject value = objectBase.getField(instruction.getFieldInfo().getIndex());
            setLocalVariable(instruction.getTo().getIndex(), value);
        }
        else
            throw new RuntimeException("The given field does not belong to given type.");
    }

    private void executeInvoke(Invoke invoke) {
        TObject instance = getLocalVariable(invoke.getInstance().getIndex());

        int parameterCount = invoke.getParameters().length;
        TObject[] parameters = new TObject[parameterCount];
        for (int i = 0; i < parameterCount; i++)
            parameters[i] = getLocalVariable(invoke.getParameters()[i].getIndex());

        CompletableFuture<TObject> returnFuture = machInvokeOnMachineThread(
                invoke.getMethodInfo(),
                instance,
                parameters
        );

        returnFuture.thenAccept(value -> {
            VariableInfo resultVariable = invoke.getTo();
            if (resultVariable != null)
                setLocalVariable(resultVariable.getIndex(), value);
        });
    }

    private void executeReturn(Return instruction) {
        if (instruction.getValue() != null) {
            this.activeMethodFrame.setReturnValue(getLocalVariable(instruction.getValue().getIndex()));
        }

        // Unroll the stack up to current method.
        Frame frame = this.activeFrame;
        while (frame != this.activeMethodFrame) {
            exitFrame(frame);
            frame = frame.getCaller();
        }
        this.activeFrame = this.activeMethodFrame;
    }

    private void executeNew(New instruction) {
        try {
            TypeInfo typeInfo = instruction.getTypeInfo();

            // Allocate the instance first.
            TObject instance = typeInfo.allocateInstance();

            // Call the internal instance constructor.
            CompletableFuture<TObject> internalConstructorReturn =
                    machInvokeOnMachineThread(typeInfo.getInternalInstanceConstructor(), instance);

            // Try to see if there is a native constructor.
            VariableInfo[] args = instruction.getArgs();
            Method nativeConstructor = nativeMethodCache.findConstructor(typeInfo.getImplementingClass(), args.length);
            if (nativeConstructor != null) {
                internalConstructorReturn.thenCompose(v -> {
                    TObject[] argValues = Stream.of(args).map(a -> getLocalVariable(a.getIndex())).toArray(TObject[]::new);
                    return invokeNative(nativeConstructor, instance, argValues);
                });
            }

            // TODO: Try to see if there is a Mac constructor.
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Invoke a native method.
     * @param method
     * @param instance
     * @param args
     * @return
     */
    private CompletableFuture<TObject> invokeNative(Method method, TObject instance, TObject ... args) {
        try {
            Object result = method.invoke(instance, Arrays.copyOf(args, args.length, Object[].class));

            if (result instanceof CompletableFuture<?>) {
                CompletableFuture<TObject> returnFuture = (CompletableFuture<TObject>)result;

                NativeMethodFrame nativeMethodFrame = new NativeMethodFrame()
                        .setCaller(this.activeFrame.getActiveNativeMethodFrame())
                        .setReturnFuture(returnFuture);
                this.activeFrame.setActiveNativeMethodFrame(nativeMethodFrame);

                return returnFuture;
            }
            else {
                CompletableFuture<TObject> returnFuture = new CompletableFuture<>();
                returnFuture.completeExceptionally(new Exception("invalid native method"));
                return returnFuture;
            }
        }
        catch (Exception exception) {
            CompletableFuture<TObject> returnFuture = new CompletableFuture<>();
            returnFuture.completeExceptionally(exception);
            return returnFuture;
        }
    }
}
