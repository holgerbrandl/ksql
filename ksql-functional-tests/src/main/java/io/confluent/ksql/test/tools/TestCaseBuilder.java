/*
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.test.tools;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.confluent.ksql.function.FunctionRegistry;
import io.confluent.ksql.test.model.PostConditionsNode;
import io.confluent.ksql.test.model.TestCaseNode;
import io.confluent.ksql.test.tools.conditions.PostConditions;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.Matcher;

/**
 * Builds {@link TestCase}s from {@link TestCaseNode}.
 */
public final class TestCaseBuilder {

  private final FunctionRegistry functionRegistry = TestFunctionRegistry.INSTANCE.get();

  public List<TestCase> buildTests(final TestCaseNode test, final Path testPath) {
    if (!test.isEnabled()) {
      return ImmutableList.of();
    }

    try {
      final Stream<Optional<String>> formats = test.formats().isEmpty()
          ? Stream.of(Optional.empty())
          : test.formats().stream().map(Optional::of);

      return formats
          .map(format -> createTest(test, format, testPath))
          .collect(Collectors.toList());
    } catch (final Exception e) {
      throw new AssertionError("Invalid test '" + test.name() + "': " + e.getMessage(), e);
    }
  }

  private TestCase createTest(
      final TestCaseNode test,
      final Optional<String> explicitFormat,
      final Path testPath
  ) {
    final String testName = TestCaseBuilderUtil.buildTestName(
        testPath,
        test.name(),
        explicitFormat
    );

    try {
      final List<String> statements = TestCaseBuilderUtil.buildStatements(
          test.statements(),
          explicitFormat
      );

      final Optional<Matcher<Throwable>> ee = test.expectedException()
          .map(een -> een.build(Iterables.getLast(statements)));

      final Map<String, Topic> topics = TestCaseBuilderUtil.getTopicsByName(
          statements,
          test.topics(),
          test.outputs(),
          test.inputs(),
          explicitFormat,
          ee.isPresent(),
          functionRegistry
      );

      final List<Record> inputRecords = test.inputs().stream()
          .map(node -> node.build(topics))
          .collect(Collectors.toList());

      final List<Record> outputRecords = test.outputs().stream()
          .map(node -> node.build(topics))
          .collect(Collectors.toList());

      final PostConditions post = test.postConditions()
          .map(PostConditionsNode::build)
          .orElse(PostConditions.NONE);

      return new TestCase(
          testPath,
          testName,
          Optional.empty(),
          test.properties(),
          topics.values(),
          inputRecords,
          outputRecords,
          statements,
          ee,
          post
      );
    } catch (final Exception e) {
      throw new AssertionError(testName + ": Invalid test. " + e.getMessage(), e);
    }
  }
}
