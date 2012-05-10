
// fix for http://jira.grails.org/browse/GRAILS-7309 issue
// author pedjak@gmail.com

import java.beans.Introspector
import java.beans.PropertyDescriptor
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import grails.test.MockUtils
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import grails.validation.ValidationException

eventTestPhasesStart = {
    // patch MockUtils.addDynamicInstanceMethods before executing any test
    def fixedAddDynamicInstanceMethods = { mockUtils, Class clazz, List testInstances ->
        // Add save() method.
        clazz.metaClass.save = { Map args = [:] ->
	    
            if(validate()) {
		mockUtils.triggerEvent delegate, 'beforeValidate'
                def properties = Introspector.getBeanInfo(clazz).propertyDescriptors
                def mapping = mockUtils.evaluateMapping(clazz)

                boolean isInsert
                if (mapping?.id?.generator == "assigned") {
                    isInsert = !testInstances.contains(delegate)
                } else {
                    isInsert = !delegate.id
                }

                if(isInsert) {
                    mockUtils.triggerEvent delegate, 'beforeInsert'
                    if (!testInstances.contains(delegate)) {
                        testInstances << delegate
                        mockUtils.setId delegate, clazz
                    }
                    mockUtils.setTimestamp delegate, 'dateCreated', properties, mapping
                    mockUtils.setTimestamp delegate, 'lastUpdated', properties, mapping
                    mockUtils.triggerEvent delegate, 'afterInsert'
                } else {
                    mockUtils.triggerEvent delegate, 'beforeUpdate'
                    mockUtils.setTimestamp delegate, 'lastUpdated', properties, mapping
                    mockUtils.triggerEvent delegate, 'afterUpdate'
                }
                
                return delegate
            } else if (args.failOnError) {
                throw new ValidationException("Validation Error(s) occurred during save()", delegate.errors)
            }
            return null
        }

        // Add delete() method.
        clazz.metaClass.delete = { Map args = [:] ->
            for (int i in 0..<testInstances.size()) {
                if (testInstances[i] == delegate) {
                    mockUtils.triggerEvent delegate, 'beforeDelete'
                    testInstances.remove(i)
                    mockUtils.triggerEvent delegate, 'afterDelete'
                    break;
                }
            }
        }

        // these don't need to do anything.
        clazz.metaClass.discard = {-> delegate }
        clazz.metaClass.refresh = {-> delegate }
        clazz.metaClass.attach = {-> delegate }

        // instanceOf() method - just delegates to regular operator
        clazz.metaClass.instanceOf = { Class c -> c.isInstance(delegate) }

        // Add the "addTo*" and "removeFrom*" methods.

        def collectionTypes = [:]
        def hasMany = GrailsClassUtils.getStaticPropertyValue(clazz, 'hasMany')
        if (hasMany) {
            for (name in hasMany.keySet()) {
                // pre-populate with Set, override with PropertyDescriptors below
                collectionTypes[name] = Set
            }
        }

        Introspector.getBeanInfo(clazz).propertyDescriptors.each { PropertyDescriptor pd ->
            if (Collection.isAssignableFrom(pd.propertyType)) {
                collectionTypes[pd.name] = pd.propertyType
            }
        }

        collectionTypes.each { String propertyName, propertyType ->
            // Capitalise the name of the property.
            def collectionName = propertyName[0].toUpperCase() + propertyName[1..-1]

            clazz.metaClass."addTo$collectionName" = { arg ->
                def obj = delegate
                if (obj."$propertyName" == null) {
                    obj."$propertyName" = GrailsClassUtils.createConcreteCollection(propertyType)
                }

                def instanceClass
                if (arg instanceof Map) {
                    instanceClass = hasMany[propertyName]
                    arg = createFromMap(arg, instanceClass)
                }
                else {
                    instanceClass = arg.getClass()
                }

                obj."$propertyName" << arg

                // now set back-reference
                if (!(arg instanceof Map)) {
                    def otherHasMany = GrailsClassUtils.getStaticPropertyValue(instanceClass, 'hasMany')
                    boolean found = false
                    if (otherHasMany) {
                        // many-to-many
                        otherHasMany.each { String otherCollectionName, Class otherCollectionType ->
                            if (clazz.isAssignableFrom(otherCollectionType) && clazz != otherCollectionType) {
                                if (arg."$otherCollectionName" == null) {
                                    arg."$otherCollectionName" = GrailsClassUtils.createConcreteCollection(otherCollectionType)
                                }
                                arg."$otherCollectionName" << obj
                                found = true
                            }
                        }
                    }
                    // PATCH: if not found among hasMany properties, try 1-many 
                    if (!found) {
                        // 1-many
                        for (PropertyDescriptor pd in Introspector.getBeanInfo(instanceClass).propertyDescriptors) {
                            if (clazz.isAssignableFrom(pd.propertyType)) {
                                arg[pd.name] = obj
                            }
                        }
                    }
                }

                return obj
            }

            clazz.metaClass."removeFrom$collectionName" = { arg ->
                if (arg instanceof Map) {
                    arg = createFromMap(arg, hasMany[propertyName])
                }

                delegate."$propertyName"?.remove(arg)
                return delegate
            }
        }
    }
    
    // replace mockDomain method in MockUtils class that calls patched addDynamicInstanceMethods()
    // this is neccessary because addDynamicInstanceMethods is declared as private static
    // and groovy metaprogramming capabilities still cannot replace such methods.
    // otherwise, it would be enough to replace the problematic method only
    // Hence, the provided mockDomain method is almost identical to the original, except
    // in places where we invoked the patched addDynamicInstanceMethods()
    MockUtils.metaClass.'static'.mockDomain = { Class clazz, Map errorsMap, List testInstances = [] ->
        mockUtils = delegate
         
        def dc = new DefaultGrailsDomainClass(clazz)
        
        def rootInstances = testInstances.findAll { clazz.isInstance(it) }
        def childInstances = testInstances.findAll { clazz.isInstance(it) && it.class != clazz }.groupBy { it.class }

        mockUtils.TEST_INSTANCES[clazz] = rootInstances
        mockUtils.addDynamicFinders(clazz, rootInstances)
        mockUtils.addGetMethods(clazz, dc, rootInstances)
        mockUtils.addCountMethods(clazz, dc, rootInstances)
        mockUtils.addListMethod(clazz, rootInstances)
        mockUtils.addValidateMethod(clazz, dc, errorsMap, rootInstances)
	clazz.metaClass.validate = { ->
		mockUtils.triggerEvent(delegate, 'beforeValidate')
		validate([:])
	}
        // calling patched method
        fixedAddDynamicInstanceMethods(mockUtils, clazz, rootInstances)
        mockUtils.addOtherStaticMethods(clazz, rootInstances)

        // Note that if the test instances are of type "clazz", they
        // will not have the extra dynamic methods because they were
        // created before the methods were added to the class.
        //
        // So, for each test object that is an instance of "clazz", we
        // manually change its metaclass to "clazz"'s so that it gets
        // the extra methods.
        mockUtils.updateMetaClassForClass(rootInstances, clazz)

        childInstances.each { Class childClass, List instances ->
            mockUtils.TEST_INSTANCES[childClass] = instances
            def childDomain = new DefaultGrailsDomainClass(childClass)
            mockUtils.addDynamicFinders(childClass, instances)
            mockUtils.addGetMethods(childClass, childDomain, instances)
            mockUtils.addCountMethods(childClass, childDomain, instances)
            mockUtils.addListMethod(childClass, instances)
            mockUtils.addValidateMethod(childClass, childDomain, errorsMap, instances)
            // calling patched method
            fixedAddDynamicInstanceMethods(mockUtils, childClass, instances)
            mockUtils.addOtherStaticMethods(childClass, instances)
            mockUtils.updateMetaClassForClass(rootInstances, childClass)
        }

        return dc
    }
    
    
}