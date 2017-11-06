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

/**
 * An interface that must be implemented when a {@linkplain DataSourceFactory} wants to be
 * notified by {@code DataSourceFactory.class}.
 *<P>
 * A {@code DataSourceFactoryAction} implementation is not intended to be used
 * directly by applications. A DataSourceFactory  may choose
 * to create its {@code DataSourceFactoryAction} implementation in a private class
 * to avoid it being called directly.
 * <p>
 * A DataSourceFactory static initialization block must call
 * {@linkplain DataSourceFactory#registerDataSourceFactory(java.sql2.DataSourceFactory, java.sql2.DataSourceFactoryAction) } in order
 * to inform {@code DataSourceFactory} which {@code DataSourceFactoryAction} implementation to
 * call when the DataSourceFactory is de-registered.
 * @since 1.8
 */
public interface DataSourceFactoryAction {
    /**
     * Method called by
     * {@linkplain DataSourceFactory#deregisterDataSourceFactory(DataSourceFactory) }
     *  to notify the DataSourceFactory that it was de-registered.
     * <p>
     * The {@code deregister} method is intended only to be used by DataSourceFactories
     * and not by applications.  Implementations are recommended to not implement
     * {@code DataSourceFactoryAction} in a public class.  If there are active
     * connections to the database at the time that the {@code deregister}
     * method is called, it is implementation specific as to whether the
     * connections are closed or allowed to continue. Once this method is
     * called, it is implementation specific as to whether the implementation may
     * limit the ability to create new connections to the database, invoke
     * other {@code DataSourceFactory} methods or throw a {@code Exception}s.
     * Consult your implementation vendor's documentation for additional information
     * on its behavior.
     * @see DataSourceFactory#registerDataSourceFactory(java.sql.DataSourceFactory, java.sql.DataSourceFactoryAction)
     * @see DataSourceFactory#deregisterDataSourceFactory(DataSourceFactory)
     * @since 1.8
     */
    void deregister();

}