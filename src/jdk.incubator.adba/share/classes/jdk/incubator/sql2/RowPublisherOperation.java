/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jdk.incubator.sql2;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

public interface RowPublisherOperation<T> extends Operation<T> {

  /**
   * * DRAFT Subscribe to the stream of Rows returned by this Operation. The
   * result of this Operation is the value of the {@code result} parameter.
   *
   * @param subscriber Not null.
   * @param result Not null.
   * @return this RowPublisherOperation
   */
  public RowPublisherOperation<T> subscribe(Flow.Subscriber<? super Result.RowColumn> subscriber,
                                            CompletionStage<? extends T> result);
  // Covariant overrides
  
  @Override
  public RowPublisherOperation<T> onError(Consumer<Throwable> handler);
  
  @Override
  public RowPublisherOperation<T> timeout(Duration minTime);
  
}
