/*
 * Copyright (c)  2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.sql2;

import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
//TODO import jdk.internal.reflect.CallerSensitive;
//TODO import jdk.internal.reflect.Reflection;

/**
 * This class manages a list of registered DataSourceFactories.
 */
class DataSourceFactoryManager {
  
  private static final DataSourceFactoryManager SOLE_INSTANCE = new DataSourceFactoryManager();  
  private static final Permission DEREGISTER_DATASOURCEFACTORY_PERMISSION = null;  
    
  static DataSourceFactoryManager instance() {
    return SOLE_INSTANCE;
  }
  
  private final CopyOnWriteArrayList<FactoryInfo> registeredDataSourceFactories =
          new CopyOnWriteArrayList<>();
  
  private DataSourceFactoryManager() {
  }

  /*HACK*/ void println(Object ... x) { }

      /**
     * Attempts to locate a factory with the given name.
     * The <code>DataSourceFactoryManager</code> attempts to select an appropriate factory from
     * the set of registered ADBA factories.
     *
     * @param name the name of a DataSourceFactory
     */
//TODO    @CallerSensitive
    DataSourceFactory forName(String name) {

        println("DataSourceFactoryManager.getDataSourceFactory(\"" + name + "\")");

        Class<?> callerClass = null; //TODO Reflection.getCallerClass();

        // Walk through the loaded registeredDataSourceFactories attempting to locate
        // one with the given name
        for (FactoryInfo aDataSourceFactory : registeredDataSourceFactories) {
            // If the caller does not have permission to load the factory then
            // skip it.
            if(isDataSourceFactoryAllowed(aDataSourceFactory.factory, callerClass)) {
                    if(aDataSourceFactory.factory.getName().equals(name)) {
                        // Success!
                        println("getDataSourceFactory returning " + aDataSourceFactory.factory.getClass().getName());
                    return (aDataSourceFactory.factory);
                    }

            } else {
                println("    skipping: " + aDataSourceFactory.factory.getClass().getName());
            }
        }
        println("getDataSourceFactory: no suitable factory");
        return null;
    }



  // Indicates whether the class object that would be created if the code calling
    // DataSourceFactoryManager is accessible.
    private boolean isDataSourceFactoryAllowed(DataSourceFactory factory, Class<?> caller) {
        ClassLoader callerCL = caller != null ? caller.getClassLoader() : null;
        return isDataSourceFactoryAllowed(factory, callerCL);
    }

    private boolean isDataSourceFactoryAllowed(DataSourceFactory factory, ClassLoader classLoader) {
        boolean result = false;
        if(factory != null) {
            Class<?> aClass = null;
            try {
                aClass =  Class.forName(factory.getClass().getName(), true, classLoader);
            } catch (Exception ex) {
                result = false;
            }
             result = ( aClass == factory.getClass() ) ? true : false;
        }
        return result;
    }

    private void loadInitialDataSourceFactories() {
        String factories;
        try {
            factories = AccessController.doPrivileged(
                    (PrivilegedAction<String>) () -> System.getProperty("adba.factories"));
        } catch (Exception ex) {
            factories = null;
        }
        // If the factory is packaged as a Service Provider, load it.
        // Get all the factories through the classloader
        // exposed as a java.sql2.DataSourceFactory.class service.
        // ServiceLoader.load() replaces the sun.misc.Providers()

        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
          ServiceLoader<DataSourceFactoryManager> loadedDataSourceFactories = ServiceLoader.load(DataSourceFactoryManager.class);
          Iterator<DataSourceFactoryManager> factoriesIterator = loadedDataSourceFactories.iterator();
          
          /* Load these factories, so that they can be instantiated.
          * It may be the case that the factory class may not be there
          * i.e. there may be a packaged factory with the service class
          * as implementation of java.sql2.DataSourceFactory but the actual class
          * may be missing. In that case a java.util.ServiceConfigurationError
          * will be thrown at runtime by the VM trying to locate
          * and load the service.
          *
          * Adding a try catch block to catch those runtime errors
          * if factory not available in classpath but it's
          * packaged as service and that service is there in classpath.
          */
          try{
            while(factoriesIterator.hasNext()) {
              factoriesIterator.next();
            }
          } catch(Throwable t) {
            // Do nothing
          }
          return null;
        });

        println("DataSourceFactoryManager.initialize: adba.factories = " + factories);

        if (factories == null || factories.equals("")) {
            return;
        }
        String[] factoriesList = factories.split(":");
        println("number of DataSourceFactories:" + factoriesList.length);
        for (String aDataSourceFactory : factoriesList) {
            try {
                println("DataSourceFactoryManager.Initialize: loading " + aDataSourceFactory);
                Class.forName(aDataSourceFactory, true,
                        ClassLoader.getSystemClassLoader());
            } catch (Exception ex) {
                println("DataSourceFactoryManager.Initialize: load failed: " + ex);
            }
        }
    }

    /**
     * Registers the given factory with the {@code DataSourceFactoryManager}.
     * A newly-loaded factory class should call
     * the method {@code registerDataSourceFactory} to make itself
     * known to the {@code DataSourceFactoryManager}. If the factory is currently
     * registered, no action is taken.
     *
     * @param factory the new JDBC DataSourceFactory that is to be registered with the
     *               {@code DataSourceFactoryManager}
     * @exception SQLException if a database access error occurs
     * @exception NullPointerException if {@code factory} is null
     */
    synchronized void registerDataSourceFactory(DataSourceFactory factory) {

        registerDataSourceFactory(factory, null);
    }

    /**
     * Registers the given factory with the {@code DataSourceFactoryManager}.
     * A newly-loaded factory class should call
     * the method {@code registerDataSourceFactory} to make itself
     * known to the {@code DataSourceFactoryManager}. If the factory is currently
     * registered, no action is taken.
     *
     * @param factory the new JDBC DataSourceFactory that is to be registered with the
     *               {@code DataSourceFactoryManager}
     * @param da     the {@code DataSourceFactoryAction} implementation to be used when
     *               {@code DataSourceFactoryManager#deregisterDataSourceFactory} is called
     * @exception SQLException if a database access error occurs
     * @exception NullPointerException if {@code factory} is null
     * @since 1.8
     */
     synchronized void registerDataSourceFactory(DataSourceFactory factory,
            DataSourceFactoryAction da) {

        /* Register the factory if it has not already been added to our list */
        if(factory != null) {
            registeredDataSourceFactories.addIfAbsent(new FactoryInfo(factory, da));
        } else {
            // This is for compatibility with the original DataSourceFactoryManager
            throw new NullPointerException();
        }

        println("registerDataSourceFactory: " + factory);

    }

    /**
     * Removes the specified factory from the {@code DataSourceFactoryManager}'s list of
     * registered factories.
     * <p>
     * If a {@code null} value is specified for the factory to be removed, then no
     * action is taken.
     * <p>
     * If a security manager exists and its {@code checkPermission} denies
     * permission, then a {@code SecurityException} will be thrown.
     * <p>
     * If the specified factory is not found in the list of registered factories,
     * then no action is taken.  If the factory was found, it will be removed
     * from the list of registered factories.
     * <p>
     * If a {@code DataSourceFactoryAction} instance was specified when the JDBC factory was
     * registered, its deregister method will be called
     * prior to the factory being removed from the list of registered factories.
     *
     * @param factory the JDBC DataSourceFactory to remove
     * @exception SQLException if a database access error occurs
     * @throws SecurityException if a security manager exists and its
     * {@code checkPermission} method denies permission to deregister a factory.
     *
     * @see SecurityManager#checkPermission
     */
//TODO    @CallerSensitive
    synchronized void deregisterDataSourceFactory(DataSourceFactory factory) {
        if (factory == null) {
            return;
        }

        SecurityManager sec = System.getSecurityManager();
        if (sec != null) {
            sec.checkPermission(DEREGISTER_DATASOURCEFACTORY_PERMISSION);
        }

        println("DataSourceFactoryManager.deregisterDataSourceFactory: " + factory);

        FactoryInfo aDataSourceFactory = new FactoryInfo(factory, null);
        if(registeredDataSourceFactories.contains(aDataSourceFactory)) {
            if (isDataSourceFactoryAllowed(factory, (Class)null)) { //TODO Reflection.getCallerClass())) {
                FactoryInfo di = registeredDataSourceFactories.get(registeredDataSourceFactories.indexOf(aDataSourceFactory));
                 // If a DataSourceFactoryAction was specified, Call it to notify the
                 // factory that it has been deregistered
                 if(di.action() != null) {
                     di.action().deregister();
                 }
                 registeredDataSourceFactories.remove(aDataSourceFactory);
            } else {
                // If the caller does not have permission to load the factory then
                // throw a SecurityException.
                throw new SecurityException();
            }
        } else {
            println("    couldn't find factory to unload");
        }
    }
}
/*
 * Wrapper class for registered DataSourceFactories in order to not expose DataSourceFactory.equals()
 * to avoid the capture of the DataSourceFactory it being compared to as it might not
 * normally have access.
 */
class FactoryInfo {

    final DataSourceFactory factory;
    DataSourceFactoryAction da;
    FactoryInfo(DataSourceFactory factory, DataSourceFactoryAction action) {
        this.factory = factory;
        da = action;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof FactoryInfo)
                && this.factory == ((FactoryInfo) other).factory;
    }

    @Override
    public int hashCode() {
        return factory.hashCode();
    }

    @Override
    public String toString() {
        return ("factory[className="  + factory + "]");
    }

    DataSourceFactoryAction action() {
        return da;
    }
} 
