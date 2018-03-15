package com.squarebit.machinations.machc.avm;

import com.squarebit.machinations.machc.avm.exceptions.FieldAlreadyExistedException;
import com.squarebit.machinations.machc.avm.exceptions.MethodAlreadyExistedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A type declared in a {@link ModuleInfo}.
 */
public final class TypeInfo {
    private ModuleInfo module;
    private String name;

    //////////////////////
    // Fields
    private List<FieldInfo> fields;
    private Map<String, FieldInfo> fieldByName;

    /////////////////////
    // Methods
    private List<MethodInfo> methods;
    private Map<String, MethodInfo> methodByName;

    /**
     * Instantiates a new object.
     */
    public TypeInfo() {
        this.fields = new ArrayList<>();
        this.fieldByName = new HashMap<>();

        this.methods = new ArrayList<>();
        this.methodByName = new HashMap<>();
    }

    /**
     * Gets the containing {@link ModuleInfo}.
     *
     * @return the containing module
     */
    public ModuleInfo getModule() {
        return module;
    }

    /**
     * Sets the containing {@link ModuleInfo}.
     *
     * @param module the containing module
     * @return this instance
     * @apiNote it is not recommended to use this method directly. Use {@link ModuleInfo#addType(TypeInfo)}
     * or {@link ModuleInfo#createType(String)} instead.
     *
     */
    public TypeInfo setModule(ModuleInfo module) {
        this.module = module;
        return this;
    }

    /**
     * Gets the type name.
     *
     * @return the type name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the type name.
     *
     * @param name the type name
     * @return this instance
     * @apiNote it is not recommended to use this method directly. Use {@link ModuleInfo#addType(TypeInfo)}
     * or {@link ModuleInfo#createType(String)} instead.
     */
    public TypeInfo setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Gets {@link FieldInfo} declared in this type.
     *
     * @return the field list in declaration order
     */
    public List<FieldInfo> getFields() {
        return fields;
    }

    /**
     * Create a new {@link FieldInfo} in this type.
     *
     * @param name the field name
     * @return the {@link FieldInfo} instance
     * @throws FieldAlreadyExistedException if a field with given name already existed.
     */
    public FieldInfo createField(String name) throws FieldAlreadyExistedException {
        FieldInfo fieldInfo = new FieldInfo().setDeclaringType(this).setName(name);
        addField(fieldInfo);
        return fieldInfo;
    }

    /**
     * Adds a {@link FieldInfo} to this type.
     *
     * @param fieldInfo the {@link FieldInfo} instance
     * @throws FieldAlreadyExistedException if a field with given name already existed.
     */
    public void addField(FieldInfo fieldInfo) throws FieldAlreadyExistedException {
        if (fieldByName.containsKey(fieldInfo.getName()))
            throw new FieldAlreadyExistedException(this, fieldInfo.getName());

        fieldInfo.setDeclaringType(this);
        fields.add(fieldInfo);
        fieldByName.put(fieldInfo.getName(), fieldInfo);
    }

    /**
     * Gets all methods declared in this type.
     *
     * @return the method list in declaration order
     */
    public List<MethodInfo> getMethods() {
        return methods;
    }

    /**
     * Creates a new {@link MethodInfo} in this type.
     *
     * @param name the method name
     * @return the {@link MethodInfo} instance
     * @throws MethodAlreadyExistedException the method already existed exception
     */
    public MethodInfo createMethod(String name) throws MethodAlreadyExistedException {
        MethodInfo methodInfo = new MethodInfo().setName(name);
        addMethod(methodInfo);
        return methodInfo;
    }

    /**
     * Adds a {@link MethodInfo} to this type.
     *
     * @param methodInfo the {@link MethodInfo} instance
     * @throws MethodAlreadyExistedException if a method with given name already existed.
     */
    public void addMethod(MethodInfo methodInfo) throws MethodAlreadyExistedException {
        if (methodByName.containsKey(methodInfo.getName()))
            throw new MethodAlreadyExistedException(this, methodInfo.getName());

        methodInfo.setDeclaringType(this);
        methods.add(methodInfo);
        methodByName.put(methodInfo.getName(), methodInfo);
    }
}