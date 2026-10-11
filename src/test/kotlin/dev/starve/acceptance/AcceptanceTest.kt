package dev.starve.acceptance

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/** Runs every feature file under specs/features through the step definitions in this package. */
@Suite
@SelectClasspathResource("features")
@IncludeEngines("cucumber")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.starve.acceptance")
class AcceptanceTest
