/*
 * Copyright (c)  2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.sql2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Simple example code using various aspects of ADBA. These do not necessarily
 * demonstrate the best way to use each feature, just one way.
 */
public class Examples {

  // DataSourceFactory
  public DataSource getDataSource() {
    return DataSourceFactory.newFactory("oracle.database.adba")
            .builder()
            .url("//host.oracle.com:5521/example")
            .username("scott")
            .password("tiger")
            .build();
  }

  // RowCountOperation
  public void insertItem(Session session, Item item) {
    session.rowCountOperation("insert into tab values (:id, :name, :answer)")
            .set("id", item.id(), AdbaType.NUMERIC)
            .set("name", item.name(), AdbaType.VARCHAR)
            .set("answer", item.answer(), AdbaType.NUMERIC)
            .submit();
  }

  // RowOperation
  public void idsForAnswer(DataSource ds, List<Integer> result, int correctAnswer) {
    try (Session session = ds.getSession()) {
      session.<List<Integer>>rowOperation("select id, name, answer from tab where answer = :target")
              .set("target", correctAnswer, AdbaType.NUMERIC)
              .collect(() -> result,
                       (list, row) -> list.add(row.at("id").get(Integer.class)))
              .submit();
    }
  }

  // RowOperation
  public CompletionStage<List<Item>> itemsForAnswer(DataSource ds, int answer) {
    try (Session session = ds.getSession()) {
      return session.<List<Item>>rowOperation("select id, name, answer from tab where answer = :target")
              .set("target", 42, AdbaType.NUMERIC)
              .collect(Collectors.mapping(
                      row -> new Item(row.at("id").get(Integer.class),
                                      row.at("name").get(String.class),
                                      row.at("answer").get(Integer.class)),
                      Collectors.toList()))
              .submit()
              .getCompletionStage();
    }
  }

  // Independent OperationGroup
  public void insertItemsIndependent(DataSource ds, List<Item> list) {
    String sql = "insert into tab values (:id, :name, :answer)";
    try (Session session = ds.getSession();
      OperationGroup<Void, Void> group = 
        session.<Void, Void>operationGroup().independent()) {
      for (Item elem : list) {
        group.rowCountOperation(sql)
                .set("id", elem.id)
                .set("name", elem.name)
                .set("answer", elem.answer)
                .submit()
                .getCompletionStage()
                .exceptionally(t -> {
                  System.out.println(elem.id);
                  return null;
                });
      }
      group.submit();
    }
  }

  // Held OperationGroup
  public void insertItemsHold(DataSource ds, List<Item> list) {
    String sql = "insert into tabone values (:id, :name, :answer)";
    try (Session session = ds.getSession();
      OperationGroup<Void, Void> group = 
        session.<Void, Void>operationGroup().independent()) {
      
      // Submit the group before any member is submitted. Each member 
      // operation executes immediately upon submission.
      group.submit();
      for (Item elem : list) {
        group.rowCountOperation(sql)
                .set("elem_", elem)
                .submit()
                .getCompletionStage()
                .exceptionally(t -> {
                  System.out.println(elem.id);
                  return null;
                });
      }
    }
  }

  // Parallel, Independent OperationGroup
  public void updateListParallel(List<Item> list, DataSource ds) {
    String query = "select id from tab where answer = :answer";
    String update = "update tab set name = :name where id = :id";
    try (Session session = ds.getSession();
      OperationGroup<Object, Object> group = session.operationGroup()
              .independent()
              .parallel()) {
      group.submit();
      for (Item elem : list) {
        CompletionStage<Integer> idPromise = group.<List<Integer>>rowOperation(query)
                .set("answer", elem.answer, AdbaType.NUMERIC)
                .collect(Collector.of(
                        () -> new ArrayList<>(),
                        (l, row) -> l.add(row.at("id").get(Integer.class)),
                        (l, r) -> l))
                .submit()
                .getCompletionStage()
                .thenApply(l -> l.get(0));
        group.rowCountOperation(update)
                .set("id", idPromise)
                .set("name", "the ultimate question")
                .submit()
                .getCompletionStage()
                .exceptionally(t -> {
                  System.out.println(elem.id);
                  return null;
                });
      }
    }
  }

  // TransactionCompletion
  public void transaction(DataSource ds) {
    try (Session session = ds.getSession(t -> System.out.println("ERROR: " + t.toString()))) {
      TransactionCompletion trans = session.transactionCompletion();
      CompletionStage<Integer> idPromise = session.<Integer>rowOperation("select empno, ename from emp where ename = :1 for update")
              .set("1", "CLARK", AdbaType.VARCHAR)
              .collect(Collectors.collectingAndThen(
                      Collectors.mapping(r -> r.at("empno").get(Integer.class),
                                         Collectors.toList()),
                      l -> l.get(0)))
              .onError(t -> trans.setRollbackOnly())
              .submit()
              .getCompletionStage();
      session.<Long>rowCountOperation("update emp set deptno = :1 where empno = :2")
              .set("1", 50, AdbaType.INTEGER)
              .set("2", idPromise, AdbaType.INTEGER)
              .apply(c -> {
                if (c.getCount() != 1L) {
                  trans.setRollbackOnly();
                  throw new RuntimeException("updated wrong number of rows");
                }
                return c.getCount();
              })
              .onError(t -> trans.setRollbackOnly())
              .submit();
      //    .getCompletionStage()
      //    .exceptionally( t -> { trans.setRollbackOnly(); return null; } ) // incorrect
      session.catchErrors();
      session.commitMaybeRollback(trans);
    }
  }

  // RowPublisherOperation
  public CompletionStage<List<String>> rowSubscriber(DataSource ds) {

    String sql = "select empno, ename from emp";
    CompletableFuture<List<String>> result = new CompletableFuture<>();

    Flow.Subscriber<Result.RowColumn> subscriber = new Flow.Subscriber<>() {

      Flow.Subscription subscription;
      List<String> names = new ArrayList<>();
      int demand = 0;

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(10);
        demand += 10;
      }

      @Override
      public void onNext(Result.RowColumn column) {
        names.add(column.at("ename").get(String.class));
        if (--demand < 1) {
          subscription.request(10);
          demand += 10;
        }
      }

      @Override
      public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
      }

      @Override
      public void onComplete() {
        result.complete(names);
      }

    };

    try (Session session = ds.getSession()) {
      return session.<List<String>>rowPublisherOperation(sql)
              .subscribe(subscriber, result)
              .submit()
              .getCompletionStage();
    }
  }

  // ArrayRowCountOperation
  public CompletionStage<Long> arrayInsert(DataSource ds,
                                           List<Integer> ids,
                                           List<String> names,
                                           List<Integer> answers) {
    String sql = "insert into tab values (?, ?, ?)";
    try (Session session = ds.getSession()) {
      return session.<Long>arrayRowCountOperation(sql)
              .collect(Collectors.summingLong(c -> c.getCount()))
              .set("1", ids, AdbaType.INTEGER)
              .set("2", names, AdbaType.VARCHAR)
              .set("3", answers, AdbaType.INTEGER)
              .submit()
              .getCompletionStage();
    }
  }

  // ArrayRowCountOperation -- transposed
  public CompletionStage<Long> transposedArrayInsert(DataSource ds, List<Item> items) {
    String sql = "insert into tab values (?, ?, ?)";
    try (Session session = ds.getSession()) {
      return session.<Long>arrayRowCountOperation(sql)
              .collect(Collectors.summingLong(c -> c.getCount()))
              .set("1", items.stream().map(Item::id).collect(Collectors.toList()), AdbaType.INTEGER)
              .set("2", items.stream().map(Item::name).collect(Collectors.toList()), AdbaType.VARCHAR)
              .set("3", items.stream().map(Item::answer).collect(Collectors.toList()), AdbaType.INTEGER)
              .submit()
              .getCompletionStage();
    }
  }

  // OutOperation
  public CompletionStage<Item> getItem(DataSource ds, int id) {
    String sql = "call item_for_id(:id, :name, :answer)";
    try (Session session = ds.getSession()) {
      return session.<Item>outOperation(sql)
              .set("id", id, AdbaType.INTEGER)
              .outParameter("name", AdbaType.VARCHAR)
              .outParameter("answer", AdbaType.INTEGER)
              .apply(out -> new Item(id,
                                     out.at("name").get(String.class),
                                     out.at("answer").get(Integer.class)))
              .submit()
              .getCompletionStage();
    }
  }

  // MultiOperation
  // LocalOperation
  // Control Operation Submission Rate
  public class RecordSubscriber implements Subscriber<byte[]> {

    private final Session session;
    private OperationGroup<Long, Long> group;

    public RecordSubscriber(DataSource ds) {
      session = ds.getSession();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      group = session.<Long, Long>operationGroup()
              .independent()
              .collect(Collectors.summingLong(c -> c));
      group.submit();
      session.requestHook(subscription::request);
    }

    @Override
    public void onNext(byte[] item) {
      String insert = "insert into tab values (@record)";
      group.<Long>rowCountOperation(insert)
              .set("record", item, AdbaType.VARBINARY)
              .apply(c -> c.getCount())
              .submit();
    }

    @Override
    public void onError(Throwable t) {
      group.close();
      session.close();
    }

    @Override
    public void onComplete() {
      group.close();
      session.close();
    }
  }

  // Controlling Session creation rate
  public class ItemSubscriber implements Subscriber<Item> {

    private final DataSourceFactory factory;
    private DataSource ds;

    public ItemSubscriber(DataSourceFactory f) {
      factory = f;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      ds = factory.builder()
              .url("//host.oracle.com:5521/example")
              .username("scott")
              .password("tiger")
              .requestHook(subscription::request)
              .build();
    }

    @Override
    public void onNext(Item item) {
      try (Session s = ds.getSession()) {
        insertItem(s, item);
      }
    }

    @Override
    public void onError(Throwable t) {
      ds.close();
    }

    @Override
    public void onComplete() {
      ds.close();
    }
  }

  // SessionProperty
  public enum ExampleSessionProperty implements SessionProperty {
    LANGUAGE;

    private static final String DEFAULT_VALUE = "AMERICAN_AMERICA";

    @Override
    public Class<?> range() {
      return String.class;
    }

    @Override
    public Object defaultValue() {
      return DEFAULT_VALUE;
    }

    @Override
    public boolean isSensitive() {
      return false;
    }

    @Override
    public boolean configureOperation(OperationGroup<?, ?> group, Object value) {
      group.operation("ALTER SESSION SET NLS_LANG = "
              + group.enquoteIdentifier((String) value, false))
              .submit();
      return true;
    }

  }

  public DataSource getDataSource(DataSourceFactory factory) {
    return factory.builder()
            .registerSessionProperty(ExampleSessionProperty.LANGUAGE)
            // or .defaultSessionProperty(ExampleSessionProperty.LANGUAGE, "AMERICAN_AMERICA")
            // or .sessionProperty(ExampleSessionProperty.LANGUAGE, "FRENCHCANADIAN_CANADA")
            .build();
  }

  public Session getSession(DataSource ds) {
    return ds.builder()
            .property(ExampleSessionProperty.LANGUAGE, "FRENCH_FRANCE")
            .build()
            .attach();
  }

  // Sharding
  // TransactionOutcome
  // Column navigation
  private class Name {

    Name(String... args) {
    }
  }

  private class Address {

    Address(String... args) {
    }
  }

  private Name getName(Result.Column col) {
    return new Name(
            col.get(String.class), // title
            col.next().get(String.class), // given name
            col.next().get(String.class), // middle initial
            col.next().get(String.class), // family name
            col.next().get(String.class)); // suffix
  }

  private Address getAddress(Result.Column col) {
    List<String> a = new ArrayList<>();
    for (Result.Column c : col.slice(6)) {
      a.add(c.get(String.class));
    }
    return new Address(a.toArray(new String[0]));
  }

  @SuppressWarnings("unused")
  public void columNavigation(Result.RowColumn column) {
    Name fullName = getName(column.at("name_title"));
    Address streetAddress = getAddress(column.at("street_address_line1"));
    Address mailingAddress = getAddress(column.at("mailing_address_line1"));
    for (Result.Column c : column.at(-14)) { // dump the last 14 columns
      System.out.println("trailing column " + c.get(String.class));
    }
  }

  // Error handling
  static public class Item {

    public int id;
    public String name;
    public int answer;

    public Item(int i, String n, int a) {
      id = i;
      name = n;
      answer = a;
    }

    public int id() {
      return id;
    }

    public String name() {
      return name;
    }

    public int answer() {
      return answer;
    }
  }

}
