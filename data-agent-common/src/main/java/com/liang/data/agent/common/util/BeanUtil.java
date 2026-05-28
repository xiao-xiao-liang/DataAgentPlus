package com.liang.data.agent.common.util;

import lombok.extern.slf4j.Slf4j;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易的对象属性拷贝工具类
 * 仅用于拷贝源对象与目标对象中同名且类型兼容的字段值
 * 性能调优：
 * 1. 手动定制属性元数据解析，解决 Lombok @Accessors(chain = true) 链式调用导致原生内省无法识别 WriteMethod 的问题
 * 2. 在元数据解析期，对所有 Getter/Setter 预先调用 Method.setAccessible(true)，取消运行期的 Java 安全检查，显著提升反射执行速度
 *
 * @author 资深Java架构师
 */
@Slf4j
public class BeanUtil {

    /**
     * 自定义属性描述符缓存，替代 JDK PropertyDescriptor 以提供对非标 Setter（如链式调用）的支持
     */
    private static final Map<Class<?>, Map<String, Prop>> PROPERTY_CACHE = new ConcurrentHashMap<>();

    private BeanUtil() {
        // 私有化构造器，防止工具类被实例化
    }

    /**
     * 将源对象的属性拷贝并实例化到指定的目标类中
     *
     * @param source      源对象
     * @param targetClass 目标对象的 Class 类型
     * @param <S>         源对象类型
     * @param <T>         目标对象类型
     * @return 拷贝后的目标对象实例
     */
    public static <S, T> T copyProperties(S source, Class<T> targetClass) {
        // 1. 防御式参数校验
        S src = Optional.ofNullable(source)
                .orElseThrow(() -> new IllegalArgumentException("属性拷贝源对象（source）不能为空"));
        Class<T> clazz = Optional.ofNullable(targetClass)
                .orElseThrow(() -> new IllegalArgumentException("目标对象Class类型（targetClass）不能为空"));

        try {
            // 2. 使用无参构造器实例化目标对象
            T target = clazz.getDeclaredConstructor().newInstance();
            // 3. 复用现有的拷贝方法执行属性赋值
            copyProperties(src, target);
            return target;
        } catch (Exception e) {
            log.error("实例化目标类 [{}] 失败，请确保该类存在公共的无参构造方法，原因: {}", clazz.getName(), e.getMessage(), e);
            throw new RuntimeException("实例化目标对象失败: " + clazz.getName(), e);
        }
    }

    /**
     * 将源对象的属性拷贝到目标对象中（仅拷贝同名且类型兼容的字段）
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyProperties(Object source, Object target) {
        // 1. 防御式参数校验
        Object src = Optional.ofNullable(source)
                .orElseThrow(() -> new IllegalArgumentException("属性拷贝源对象（source）不能为空"));
        Object dest = Optional.ofNullable(target)
                .orElseThrow(() -> new IllegalArgumentException("属性拷贝目标对象（target）不能为空"));

        // 2. 获取源对象和目标对象的自定义属性信息映射
        Map<String, Prop> sourceProps = getCacheProperties(src.getClass());
        Map<String, Prop> targetProps = getCacheProperties(dest.getClass());

        // 3. 遍历源对象的所有属性
        sourceProps.forEach((fieldName, sourceProp) -> {
            // 查找目标对象中是否存在同名字段
            Prop targetProp = targetProps.get(fieldName);
            if (null != targetProp) {
                Method readMethod = sourceProp.getReadMethod();
                Method writeMethod = targetProp.getWriteMethod();

                // 只有当源对象可读、目标对象可写且类型兼容时才进行拷贝
                if (null != readMethod && null != writeMethod) {
                    Class<?> sourceReturnType = readMethod.getReturnType();
                    Class<?> targetParameterType = writeMethod.getParameterTypes()[0];

                    // 校验目标 setter 参数类型是否兼容源 getter 返回值类型
                    if (targetParameterType.isAssignableFrom(sourceReturnType)) {
                        try {
                            // 执行反射读取
                            Object value = readMethod.invoke(src);
                            // 若值非空则写入目标对象
                            if (null != value) {
                                writeMethod.invoke(dest, value);
                            }
                        } catch (Exception e) {
                            // 局部拷贝异常，记录警告日志并降级继续
                            log.warn("字段 [{}] 拷贝失败，源类型: {}, 目标类型: {}, 异常信息: {}", 
                                    fieldName, sourceReturnType.getName(), targetParameterType.getName(), e.getMessage());
                        }
                    }
                }
            }
        });
    }

    /**
     * 获取类属性的自定义元数据 Map，具备线程安全的并发缓存
     *
     * @param clazz 待解析的类
     * @return 属性名称与自定义描述符的映射 Map
     */
    private static Map<String, Prop> getCacheProperties(Class<?> clazz) {
        return PROPERTY_CACHE.computeIfAbsent(clazz, key -> {
            Map<String, Prop> propMap = new ConcurrentHashMap<>(16);
            try {
                // 1. 获取所有声明的字段，提取基本的名称与类型
                java.lang.reflect.Field[] fields = key.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    String fieldName = field.getName();
                    Prop prop = new Prop(fieldName, field.getType());
                    
                    // 反射寻找标准 getter 方法
                    Method getter = findGetter(key, fieldName, field.getType());
                    if (null != getter) {
                        // 预先取消 Java 安全检查，显著提升 Method.invoke 反射性能
                        getter.setAccessible(true);
                        prop.setReadMethod(getter);
                    }
                    
                    // 反射寻找标准 setter 方法，支持非 void 返回类型（如链式调用）
                    Method setter = findSetter(key, fieldName, field.getType());
                    if (null != setter) {
                        // 预先取消 Java 安全检查
                        setter.setAccessible(true);
                        prop.setWriteMethod(setter);
                    }
                    
                    propMap.put(fieldName, prop);
                }

                // 2. 向上内省获取父类属性描述符作为兜底补充（如继承自 BaseEntity 的字段）
                BeanInfo beanInfo = Introspector.getBeanInfo(key);
                PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
                if (null != pds) {
                    for (PropertyDescriptor pd : pds) {
                        String fieldName = pd.getName();
                        if ("class".equals(fieldName)) {
                            continue;
                        }
                        Prop prop = propMap.computeIfAbsent(fieldName, name -> new Prop(name, pd.getPropertyType()));
                        
                        if (prop.getReadMethod() == null && pd.getReadMethod() != null) {
                            pd.getReadMethod().setAccessible(true);
                            prop.setReadMethod(pd.getReadMethod());
                        }
                        
                        if (prop.getWriteMethod() == null) {
                            // 优先使用内省的 WriteMethod，若为空则在当前类手动反射寻找支持链式调用的 setter
                            Method writeMethod = pd.getWriteMethod() != null ? pd.getWriteMethod() 
                                    : findSetter(key, fieldName, pd.getPropertyType());
                            if (null != writeMethod) {
                                writeMethod.setAccessible(true);
                                prop.setWriteMethod(writeMethod);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("解析类 [{}] 的属性描述符失败，原因: {}", key.getName(), e.getMessage(), e);
            }
            return propMap;
        });
    }

    /**
     * 反射寻找 Getter 方法
     */
    private static Method findGetter(Class<?> clazz, String fieldName, Class<?> fieldType) {
        String upperName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        String getMethodName = "get" + upperName;
        try {
            return clazz.getMethod(getMethodName);
        } catch (NoSuchMethodException e) {
            // 支持布尔类型的 is 前缀方法
            if (fieldType == boolean.class || fieldType == Boolean.class) {
                String isMethodName = "is" + upperName;
                try {
                    return clazz.getMethod(isMethodName);
                } catch (NoSuchMethodException ignored) {}
            }
        }
        return null;
    }

    /**
     * 反射寻找 Setter 方法，放宽对返回值必须为 void 的限制（完美支持链式调用）
     */
    private static Method findSetter(Class<?> clazz, String fieldName, Class<?> fieldType) {
        String setMethodName = "set" + (fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
        for (Method method : clazz.getMethods()) {
            // 只要方法名称匹配、且参数仅有1个、且类型兼容即可作为 Setter
            if (method.getName().equals(setMethodName) && method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                if (paramType.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(paramType)) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 自定义属性元数据描述符
     */
    private static class Prop {
        private final String name;
        private final Class<?> type;
        private Method readMethod;
        private Method writeMethod;

        public Prop(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            return type;
        }

        public Method getReadMethod() {
            return readMethod;
        }

        public void setReadMethod(Method readMethod) {
            this.readMethod = readMethod;
        }

        public Method getWriteMethod() {
            return writeMethod;
        }

        public void setWriteMethod(Method writeMethod) {
            this.writeMethod = writeMethod;
        }
    }
}
