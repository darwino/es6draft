/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.types.builtins;

import static com.github.anba.es6draft.runtime.AbstractOperations.*;
import static com.github.anba.es6draft.runtime.internal.Errors.throwTypeError;
import static com.github.anba.es6draft.runtime.types.Null.NULL;
import static com.github.anba.es6draft.runtime.types.PropertyDescriptor.CompletePropertyDescriptor;
import static com.github.anba.es6draft.runtime.types.PropertyDescriptor.FromPropertyDescriptor;
import static com.github.anba.es6draft.runtime.types.PropertyDescriptor.ToPropertyDescriptor;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;
import static com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject.IsCompatiblePropertyDescriptor;

import java.util.Arrays;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Constructor;
import com.github.anba.es6draft.runtime.types.Null;
import com.github.anba.es6draft.runtime.types.Property;
import com.github.anba.es6draft.runtime.types.PropertyDescriptor;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Symbol;
import com.github.anba.es6draft.runtime.types.Type;

/**
 * <h1>9 Ordinary and Exotic Objects Behaviours</h1>
 * <ul>
 * <li>9.5 Proxy Object Internal Methods and Internal Slots
 * </ul>
 */
public class ExoticProxy implements ScriptObject {
    protected final Realm realm;
    /** [[ProxyTarget]] */
    protected ScriptObject proxyTarget;
    /** [[ProxyHandler]] */
    private ScriptObject proxyHandler;

    public ExoticProxy(Realm realm, ScriptObject target, ScriptObject handler) {
        this.realm = realm;
        this.proxyTarget = target;
        this.proxyHandler = handler;
    }

    protected final ScriptObject getProxyHandler(ExecutionContext cx) {
        if (proxyHandler == null) {
            // FIXME: use better error message
            throw throwTypeError(cx, Messages.Key.IncompatibleObject);
        }
        return proxyHandler;
    }

    /**
     * Revoke this proxy, that means set both, [[ProxyTarget]] and [[ProxyHandler]], to {@code null}
     * and by that prevent further operations on this proxy.
     */
    public void revoke() {
        assert this.proxyHandler != null && this.proxyTarget != null;
        this.proxyHandler = null;
        this.proxyTarget = null;
    }

    private static class CallabeExoticProxy extends ExoticProxy implements Callable {
        public CallabeExoticProxy(Realm realm, ScriptObject target, ScriptObject handler) {
            super(realm, target, handler);
        }

        /**
         * 9.5.13 [[Call]] (thisArgument, argumentsList)
         */
        @Override
        public Object call(ExecutionContext callerContext, Object thisValue, Object... args) {
            /* steps 1-2 */
            ScriptObject handler = getProxyHandler(callerContext);
            /* step 3 */
            ScriptObject target = proxyTarget;
            /* steps 4-5 */
            Callable trap = GetMethod(callerContext, handler, "apply");
            /* step 6 */
            if (trap == null) {
                return ((Callable) target).call(callerContext, thisValue, args);
            }
            /* step 7 */
            ScriptObject argArray = CreateArrayFromList(callerContext, Arrays.asList(args));
            /* step 8 */
            return trap.call(callerContext, handler, target, thisValue, argArray);
        }

        /**
         * 9.5.13 [[Call]] (thisArgument, argumentsList)
         */
        @Override
        public Object tailCall(ExecutionContext callerContext, Object thisValue, Object... args) {
            return call(callerContext, thisValue, args);
        }

        @Override
        public String toSource() {
            return ((Callable) proxyTarget).toSource();
        }
    }

    private static class ConstructorExoticProxy extends CallabeExoticProxy implements Constructor {
        public ConstructorExoticProxy(Realm realm, ScriptObject target, ScriptObject handler) {
            super(realm, target, handler);
        }

        @Override
        public boolean isConstructor() {
            // ConstructorExoticProxy is only created if [[ProxyTarget]] already has [[Construct]]
            return true;
        }

        /**
         * 9.5.14 [[Construct]] Internal Method
         */
        @Override
        public ScriptObject construct(ExecutionContext callerContext, Object... args) {
            /* steps 1-2 */
            ScriptObject handler = getProxyHandler(callerContext);
            /* step 3 */
            ScriptObject target = proxyTarget;
            /* steps 4-5 */
            Callable trap = GetMethod(callerContext, handler, "construct");
            /* step 6 */
            if (trap == null) {
                return ((Constructor) target).construct(callerContext, args);
            }
            /* step 7 */
            ScriptObject argArray = CreateArrayFromList(callerContext, Arrays.asList(args));
            /* steps 8-9 */
            Object newObj = trap.call(callerContext, handler, target, argArray);
            /* step 10 */
            if (!Type.isObject(newObj)) {
                throw throwTypeError(callerContext, Messages.Key.NotObjectType);
            }
            /* step 11 */
            return Type.objectValue(newObj);
        }

        /**
         * 9.5.14 [[Construct]] Internal Method
         */
        @Override
        public ScriptObject tailConstruct(ExecutionContext callerContext, Object... args) {
            return construct(callerContext, args);
        }
    }

    /**
     * 9.5.15 ProxyCreate(target, handler) Abstract Operation
     */
    public static ExoticProxy ProxyCreate(ExecutionContext cx, Object target, Object handler) {
        /* step 1 */
        if (!Type.isObject(target)) {
            throwTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 2 */
        if (!Type.isObject(handler)) {
            throwTypeError(cx, Messages.Key.NotObjectType);
        }
        ScriptObject proxyTarget = Type.objectValue(target);
        ScriptObject proxyHandler = Type.objectValue(handler);
        /* steps 3-7 */
        ExoticProxy proxy;
        if (IsCallable(proxyTarget)) {
            if (IsConstructor(proxyTarget)) {
                proxy = new ConstructorExoticProxy(cx.getRealm(), proxyTarget, proxyHandler);
            } else {
                proxy = new CallabeExoticProxy(cx.getRealm(), proxyTarget, proxyHandler);
            }
        } else {
            proxy = new ExoticProxy(cx.getRealm(), proxyTarget, proxyHandler);
        }
        /* step 8 */
        return proxy;
    }

    private static Property __getOwnProperty(ExecutionContext cx, ScriptObject target,
            Object propertyKey) {
        if (propertyKey instanceof String) {
            return target.getOwnProperty(cx, (String) propertyKey);
        } else {
            assert propertyKey instanceof Symbol;
            return target.getOwnProperty(cx, (Symbol) propertyKey);
        }
    }

    private static boolean __defineOwnProperty(ExecutionContext cx, ScriptObject target,
            Object propertyKey, PropertyDescriptor desc) {
        if (propertyKey instanceof String) {
            return target.defineOwnProperty(cx, (String) propertyKey, desc);
        } else {
            assert propertyKey instanceof Symbol;
            return target.defineOwnProperty(cx, (Symbol) propertyKey, desc);
        }
    }

    private static boolean __hasProperty(ExecutionContext cx, ScriptObject target,
            Object propertyKey) {
        if (propertyKey instanceof String) {
            return target.hasProperty(cx, (String) propertyKey);
        } else {
            assert propertyKey instanceof Symbol;
            return target.hasProperty(cx, (Symbol) propertyKey);
        }
    }

    private static Object __get(ExecutionContext cx, ScriptObject target, Object propertyKey,
            Object receiver) {
        if (propertyKey instanceof String) {
            return target.get(cx, (String) propertyKey, receiver);
        } else {
            assert propertyKey instanceof Symbol;
            return target.get(cx, (Symbol) propertyKey, receiver);
        }
    }

    private static boolean __set(ExecutionContext cx, ScriptObject target, Object propertyKey,
            Object value, Object receiver) {
        if (propertyKey instanceof String) {
            return target.set(cx, (String) propertyKey, value, receiver);
        } else {
            assert propertyKey instanceof Symbol;
            return target.set(cx, (Symbol) propertyKey, value, receiver);
        }
    }

    private static boolean __delete(ExecutionContext cx, ScriptObject target, Object propertyKey) {
        if (propertyKey instanceof String) {
            return target.delete(cx, (String) propertyKey);
        } else {
            assert propertyKey instanceof Symbol;
            return target.delete(cx, (Symbol) propertyKey);
        }
    }

    /**
     * Java {@code null} to {@link Null#NULL}
     */
    private static Object maskNull(Object val) {
        return (val != null ? val : NULL);
    }

    /**
     * {@link Null#NULL} to Java {@code null}
     */
    private static Object unmaskNull(Object jsval) {
        return (jsval != NULL ? jsval : null);
    }

    /**
     * 9.5.1 [[GetPrototypeOf]] ( )
     */
    @Override
    public ScriptObject getPrototypeOf(ExecutionContext cx) {
        /* steps 1-2 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 3 */
        ScriptObject target = proxyTarget;
        /* steps 4-5 */
        Callable trap = GetMethod(cx, handler, "getPrototypeOf");
        /* step 6 */
        if (trap == null) {
            return target.getPrototypeOf(cx);
        }
        /* steps 7-8 */
        Object handlerProto = trap.call(cx, handler, target);
        /* step 9 */
        if (!(Type.isNull(handlerProto) || Type.isObject(handlerProto))) {
            throw throwTypeError(cx, Messages.Key.NotObjectOrNull);
        }
        ScriptObject handlerProto_ = (ScriptObject) unmaskNull(handlerProto);
        /* steps 10-11 */
        boolean extensibleTarget = IsExtensible(cx, target);
        /* step 12 */
        if (extensibleTarget) {
            return handlerProto_;
        }
        /* step 13 */
        ScriptObject targetProto = target.getPrototypeOf(cx);
        /* step 14 */
        if (!SameValue(handlerProto_, targetProto)) {
            throw throwTypeError(cx, Messages.Key.ProxySameValue);
        }
        /* step 15 */
        return handlerProto_;
    }

    /**
     * 9.5.2 [[SetPrototypeOf]] (V)
     */
    @Override
    public boolean setPrototypeOf(ExecutionContext cx, ScriptObject prototype) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "setPrototypeOf");
        /* step 7 */
        if (trap == null) {
            return target.setPrototypeOf(cx, prototype);
        }
        /* step 8 */
        Object trapResult = trap.call(cx, handler, target, maskNull(prototype));
        /* steps 9-10 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* steps 11-12 */
        boolean extensibleTarget = IsExtensible(cx, target);
        /* step 13 */
        if (extensibleTarget) {
            return booleanTrapResult;
        }
        /* steps 14-15 */
        ScriptObject targetProto = target.getPrototypeOf(cx);
        /* step 16 */
        if (booleanTrapResult && !SameValue(prototype, targetProto)) {
            throw throwTypeError(cx, Messages.Key.ProxySameValue);
        }
        /* step 17 */
        return booleanTrapResult;
    }

    /**
     * 9.5.3 [[IsExtensible]] ( )
     */
    @Override
    public boolean isExtensible(ExecutionContext cx) {
        /* steps 1-2 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 3 */
        ScriptObject target = proxyTarget;
        /* steps 4-5 */
        Callable trap = GetMethod(cx, handler, "isExtensible");
        /* step 6 */
        if (trap == null) {
            return target.isExtensible(cx);
        }
        /* step 7 */
        Object trapResult = trap.call(cx, handler, target);
        /* steps 8-9 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* steps 10-11 */
        boolean targetResult = target.isExtensible(cx);
        /* step 12 */
        if (booleanTrapResult != targetResult) {
            throw throwTypeError(cx, Messages.Key.ProxyNotExtensible);
        }
        /* step 13 */
        return booleanTrapResult;
    }

    /**
     * 9.5.4 [[PreventExtensions]] ( )
     */
    @Override
    public boolean preventExtensions(ExecutionContext cx) {
        /* steps 1-2 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 3 */
        ScriptObject target = proxyTarget;
        /* steps 4-5 */
        Callable trap = GetMethod(cx, handler, "preventExtensions");
        /* step 6 */
        if (trap == null) {
            return target.preventExtensions(cx);
        }
        /* step 7 */
        Object trapResult = trap.call(cx, handler, target);
        /* steps 8-9 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* steps 10-11 */
        boolean targetIsExtensible = target.isExtensible(cx);
        /* step 12 */
        if (booleanTrapResult && targetIsExtensible) {
            throw throwTypeError(cx, Messages.Key.ProxyNotExtensible);
        }
        /* step 13 */
        return booleanTrapResult;
    }

    /**
     * 9.5.5 [[GetOwnProperty]] (P)
     */
    @Override
    public Property getOwnProperty(ExecutionContext cx, String propertyKey) {
        return getOwnProperty(cx, (Object) propertyKey);
    }

    /**
     * 9.5.5 [[GetOwnProperty]] (P)
     */
    @Override
    public Property getOwnProperty(ExecutionContext cx, Symbol propertyKey) {
        return getOwnProperty(cx, (Object) propertyKey);
    }

    /**
     * 9.5.5 [[GetOwnProperty]] (P)
     */
    private Property getOwnProperty(ExecutionContext cx, Object propertyKey) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "getOwnPropertyDescriptor");
        /* step 7 */
        if (trap == null) {
            return __getOwnProperty(cx, target, propertyKey);
        }
        /* steps 8-9 */
        Object trapResultObj = trap.call(cx, handler, target, propertyKey);
        /* step 10 */
        if (!(Type.isObject(trapResultObj) || Type.isUndefined(trapResultObj))) {
            throw throwTypeError(cx, Messages.Key.ProxyNotObjectOrUndefined);
        }
        /* steps 11-12 */
        Property targetDesc = __getOwnProperty(cx, target, propertyKey);
        /* step 13 */
        if (Type.isUndefined(trapResultObj)) {
            if (targetDesc == null) {
                return null;
            }
            if (!targetDesc.isConfigurable()) {
                throw throwTypeError(cx, Messages.Key.ProxyNotConfigurable);
            }
            boolean extensibleTarget = IsExtensible(cx, target);
            if (!extensibleTarget) {
                throw throwTypeError(cx, Messages.Key.ProxyNotExtensible);
            }
            return null;
        }
        if (targetDesc != null) {
            // need copy because of possible side-effects in IsExtensible()
            targetDesc = targetDesc.clone();
        }
        /* steps 14-15 */
        boolean extensibleTarget = IsExtensible(cx, target);
        /* steps 16-17 */
        PropertyDescriptor resultDesc = ToPropertyDescriptor(cx, trapResultObj);
        /* step 18 */
        CompletePropertyDescriptor(resultDesc, targetDesc);
        /* step 19 */
        boolean valid = IsCompatiblePropertyDescriptor(extensibleTarget, resultDesc, targetDesc);
        /* step 20 */
        if (!valid) {
            throw throwTypeError(cx, Messages.Key.ProxyIncompatibleDescriptor);
        }
        /* step 21 */
        if (!resultDesc.isConfigurable()) {
            if (targetDesc == null || targetDesc.isConfigurable()) {
                throw throwTypeError(cx, Messages.Key.ProxyNotConfigurable);
            }
        }
        // TODO: [[Origin]] ???
        /* step 22 */
        return resultDesc.toProperty();
    }

    /**
     * 9.5.6 [[DefineOwnProperty]] (P, Desc)
     */
    @Override
    public boolean defineOwnProperty(ExecutionContext cx, String propertyKey,
            PropertyDescriptor desc) {
        return defineOwnProperty(cx, (Object) propertyKey, desc);
    }

    /**
     * 9.5.6 [[DefineOwnProperty]] (P, Desc)
     */
    @Override
    public boolean defineOwnProperty(ExecutionContext cx, Symbol propertyKey,
            PropertyDescriptor desc) {
        return defineOwnProperty(cx, (Object) propertyKey, desc);
    }

    /**
     * 9.5.6 [[DefineOwnProperty]] (P, Desc)
     */
    private boolean defineOwnProperty(ExecutionContext cx, Object propertyKey,
            PropertyDescriptor desc) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "defineProperty");
        /* step 7 */
        if (trap == null) {
            return __defineOwnProperty(cx, target, propertyKey, desc);
        }
        /* steps 8-9 */
        Object descObj = FromPropertyDescriptor(cx, desc);
        /* step 10 */
        Object trapResult = trap.call(cx, handler, target, propertyKey, descObj);
        /* steps 11-12 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* step 13 */
        if (!booleanTrapResult) {
            return false;
        }
        /* steps 14-15 */
        Property targetDesc = __getOwnProperty(cx, target, propertyKey);
        if (targetDesc != null) {
            // need copy because of possible side-effects in IsExtensible()
            targetDesc = targetDesc.clone();
        }
        /* steps 16-17 */
        boolean extensibleTarget = IsExtensible(cx, target);
        /* steps 18-19 */
        boolean settingConfigFalse = desc.hasConfigurable() && !desc.isConfigurable();
        /* steps 20-21 */
        if (targetDesc == null) {
            if (!extensibleTarget) {
                throw throwTypeError(cx, Messages.Key.ProxyNotExtensible);
            }
            if (!desc.isConfigurable()) {
                throw throwTypeError(cx, Messages.Key.ProxyNotConfigurable);
            }
        } else {
            if (!IsCompatiblePropertyDescriptor(extensibleTarget, desc, targetDesc)) {
                throw throwTypeError(cx, Messages.Key.ProxyIncompatibleDescriptor);
            }
            if (settingConfigFalse && targetDesc.isConfigurable()) {
                throw throwTypeError(cx, Messages.Key.ProxyNotConfigurable);
            }
        }
        /* step 22 */
        return true;
    }

    /**
     * 9.5.7 [[HasProperty]] (P)
     */
    @Override
    public boolean hasProperty(ExecutionContext cx, String propertyKey) {
        return hasProperty(cx, (Object) propertyKey);
    }

    /**
     * 9.5.7 [[HasProperty]] (P)
     */
    @Override
    public boolean hasProperty(ExecutionContext cx, Symbol propertyKey) {
        return hasProperty(cx, (Object) propertyKey);
    }

    /**
     * 9.5.7 [[HasProperty]] (P)
     */
    private boolean hasProperty(ExecutionContext cx, Object propertyKey) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "has");
        /* step 7 */
        if (trap == null) {
            return __hasProperty(cx, target, propertyKey);
        }
        /* step 8 */
        Object trapResult = trap.call(cx, handler, target, propertyKey);
        /* steps 9-10 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* step 11 */
        if (!booleanTrapResult) {
            Property targetDesc = __getOwnProperty(cx, target, propertyKey);
            if (targetDesc != null) {
                if (!targetDesc.isConfigurable()) {
                    throw throwTypeError(cx, Messages.Key.ProxyNotConfigurable);
                }
                boolean extensibleTarget = IsExtensible(cx, target);
                if (!extensibleTarget) {
                    throw throwTypeError(cx, Messages.Key.ProxyNotExtensible);
                }
            }
        }
        /* step 12 */
        return booleanTrapResult;
    }

    /**
     * 9.5.8 [[Get]] (P, Receiver)
     */
    @Override
    public Object get(ExecutionContext cx, String propertyKey, Object receiver) {
        return get(cx, (Object) propertyKey, receiver);
    }

    /**
     * 9.5.8 [[Get]] (P, Receiver)
     */
    @Override
    public Object get(ExecutionContext cx, Symbol propertyKey, Object receiver) {
        return get(cx, (Object) propertyKey, receiver);
    }

    /**
     * 9.5.8 [[Get]] (P, Receiver)
     */
    private Object get(ExecutionContext cx, Object propertyKey, Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "get");
        /* step 7 */
        if (trap == null) {
            return __get(cx, target, propertyKey, receiver);
        }
        /* steps 8-9 */
        Object trapResult = trap.call(cx, handler, target, propertyKey, receiver);
        /* steps 10-11 */
        Property targetDesc = __getOwnProperty(cx, target, propertyKey);
        /* step 12 */
        if (targetDesc != null) {
            if (targetDesc.isDataDescriptor() && !targetDesc.isConfigurable()
                    && !targetDesc.isWritable()) {
                if (!SameValue(trapResult, targetDesc.getValue())) {
                    throw throwTypeError(cx, Messages.Key.ProxySameValue);
                }
            }
            if (targetDesc.isAccessorDescriptor() && !targetDesc.isConfigurable()
                    && targetDesc.getGetter() == null) {
                if (trapResult != UNDEFINED) {
                    throw throwTypeError(cx, Messages.Key.ProxyNoGetter);
                }
            }
        }
        /* step 13 */
        return trapResult;
    }

    /**
     * 9.5.9 [[Set]] ( P, V, Receiver)
     */
    @Override
    public boolean set(ExecutionContext cx, String propertyKey, Object value, Object receiver) {
        return set(cx, (Object) propertyKey, value, receiver);
    }

    /**
     * 9.5.9 [[Set]] ( P, V, Receiver)
     */
    @Override
    public boolean set(ExecutionContext cx, Symbol propertyKey, Object value, Object receiver) {
        return set(cx, (Object) propertyKey, value, receiver);
    }

    /**
     * 9.5.9 [[Set]] ( P, V, Receiver)
     */
    private boolean set(ExecutionContext cx, Object propertyKey, Object value, Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "set");
        /* step 7 */
        if (trap == null) {
            return __set(cx, target, propertyKey, value, receiver);
        }
        /* step 8 */
        Object trapResult = trap.call(cx, handler, target, propertyKey, value, receiver);
        /* steps 9-10 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* step 11 */
        if (!booleanTrapResult) {
            return false;
        }
        /* steps 12-13 */
        Property targetDesc = __getOwnProperty(cx, target, propertyKey);
        /* step 14 */
        if (targetDesc != null) {
            if (targetDesc.isDataDescriptor() && !targetDesc.isConfigurable()
                    && !targetDesc.isWritable()) {
                if (!SameValue(value, targetDesc.getValue())) {
                    throw throwTypeError(cx, Messages.Key.ProxySameValue);
                }
            }
            if (targetDesc.isAccessorDescriptor() && !targetDesc.isConfigurable()) {
                if (targetDesc.getSetter() == null) {
                    throw throwTypeError(cx, Messages.Key.ProxyNoSetter);
                }
            }
        }
        /* step 15 */
        return true;
    }

    /**
     * 9.5.10 [[Delete]] (P)
     */
    @Override
    public boolean delete(ExecutionContext cx, String propertyKey) {
        return delete(cx, (Object) propertyKey);
    }

    /**
     * 9.5.10 [[Delete]] (P)
     */
    @Override
    public boolean delete(ExecutionContext cx, Symbol propertyKey) {
        return delete(cx, (Object) propertyKey);
    }

    /**
     * 9.5.10 [[Delete]] (P)
     */
    private boolean delete(ExecutionContext cx, Object propertyKey) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 4 */
        ScriptObject target = proxyTarget;
        /* steps 5-6 */
        Callable trap = GetMethod(cx, handler, "deleteProperty");
        /* step 7 */
        if (trap == null) {
            return __delete(cx, target, propertyKey);
        }
        /* step 8 */
        Object trapResult = trap.call(cx, handler, target, propertyKey);
        /* steps 9-10 */
        boolean booleanTrapResult = ToBoolean(trapResult);
        /* step 11 */
        if (!booleanTrapResult) {
            return false;
        }
        /* steps 12-13 */
        Property targetDesc = __getOwnProperty(cx, target, propertyKey);
        /* step 14 */
        if (targetDesc == null) {
            return true;
        }
        /* step 15 */
        if (!targetDesc.isConfigurable()) {
            throw throwTypeError(cx, Messages.Key.ProxyNotConfigurable);
        }
        /* step 16 */
        return true;
    }

    /**
     * 9.5.11 [[Enumerate]] ()
     */
    @Override
    public ScriptObject enumerate(ExecutionContext cx) {
        /* steps 1-2 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 3 */
        ScriptObject target = proxyTarget;
        /* steps 4-5 */
        Callable trap = GetMethod(cx, handler, "enumerate");
        /* step 6 */
        if (trap == null) {
            return target.enumerate(cx);
        }
        /* steps 7-8 */
        Object trapResult = trap.call(cx, handler, target);
        /* step 9 */
        if (!Type.isObject(trapResult)) {
            throw throwTypeError(cx, Messages.Key.ProxyNotObject);
        }
        /* step 10 */
        return Type.objectValue(trapResult);
    }

    /**
     * 9.5.12 [[OwnPropertyKeys]] ()
     */
    @Override
    public ScriptObject ownPropertyKeys(ExecutionContext cx) {
        /* steps 1-2 */
        ScriptObject handler = getProxyHandler(cx);
        /* step 3 */
        ScriptObject target = proxyTarget;
        /* steps 4-5 */
        Callable trap = GetMethod(cx, handler, "ownKeys");
        /* step 6 */
        if (trap == null) {
            return target.ownPropertyKeys(cx);
        }
        /* steps 7-8 */
        Object trapResult = trap.call(cx, handler, target);
        /* step 9 */
        if (!Type.isObject(trapResult)) {
            throw throwTypeError(cx, Messages.Key.ProxyNotObject);
        }
        /* steps 10-11 */
        return Type.objectValue(trapResult);
    }
}
