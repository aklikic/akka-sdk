/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import akka.platform.spring.testmodels.keyvalueentity.CounterState;

import java.util.Collection;

public class EmployeeCounters {

  public final String firstName;
  public final String lastName;
  public final String email;
  public final Collection<CounterState> counters;

  @JsonCreator
  public EmployeeCounters(
      String firstName, String lastName, String email, Collection<CounterState> counters) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.counters = counters;
  }
}
