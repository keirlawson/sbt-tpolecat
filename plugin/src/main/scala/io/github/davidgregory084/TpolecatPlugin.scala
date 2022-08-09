/*
 * Copyright 2022 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.typelevel.sbt.tpolecat

import sbt.Keys._
import sbt.{ScalaVersion => _, _}

import scala.util.Try
import org.typelevel.scalacoptions._

object TpolecatPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {

    def scalacOptionsFor(
      version: String,
      modeScalacOptions: Set[ScalacOption]
    ): Seq[String] = {
      (CrossVersion.partialVersion(version), version.split('.')) match {
        case (Some((maj, min)), Array(maj2, min2, patch))
            if maj.toString == maj2 && min.toString == min2 =>
          val patchVersion  = patch.takeWhile(_.isDigit)
          val binaryVersion = ScalaVersion(maj, min, Try(patchVersion.toLong).getOrElse(0))
          ScalacOptions.tokensForVersion(binaryVersion, modeScalacOptions)
        case (Some((maj, min)), _) =>
          val binaryVersion = ScalaVersion(maj, min, 0)
          ScalacOptions.tokensForVersion(binaryVersion, modeScalacOptions)
        case (None, _) =>
          Seq.empty[String]
      }
    }

    val tpolecatDefaultOptionsMode = settingKey[OptionsMode](
      "The default mode to use for configuring scalac options via the sbt-tpolecat plugin."
    )

    val tpolecatOptionsMode = settingKey[OptionsMode](
      "The mode to use for configuring scalac options via the sbt-tpolecat plugin."
    )

    val tpolecatDevModeEnvVar = settingKey[String](
      "The environment variable to use to enable the sbt-tpolecat development mode."
    )

    val tpolecatCiModeEnvVar = settingKey[String](
      "The environment variable to use to enable the sbt-tpolecat continuous integration mode."
    )

    val tpolecatReleaseModeEnvVar = settingKey[String](
      "The environment variable to use to enable the sbt-tpolecat release mode."
    )

    val tpolecatDevModeOptions = settingKey[Set[ScalacOption]](
      "The set of scalac options that will be applied by the sbt-tpolecat plugin in the development mode."
    )

    val tpolecatCiModeOptions = settingKey[Set[ScalacOption]](
      "The set of scalac options that will be applied by the sbt-tpolecat plugin in the continuous integration mode."
    )

    val tpolecatReleaseModeOptions = settingKey[Set[ScalacOption]](
      "The set of scalac options that will be applied by the sbt-tpolecat plugin in the release mode."
    )

    val tpolecatScalacOptions = settingKey[Set[ScalacOption]](
      "The set of scalac options that will be applied by the sbt-tpolecat plugin."
    )

    val tpolecatExcludeOptions = settingKey[Set[ScalacOption]](
      "The set of scalac options that will be excluded."
    )
  }

  import autoImport._

  val commandAliases =
    addCommandAlias(
      "tpolecatDevMode",
      "set ThisBuild / tpolecatOptionsMode := _root_.org.typelevel.sbt.tpolecat.DevMode"
    ) ++
      addCommandAlias(
        "tpolecatCiMode",
        "set ThisBuild / tpolecatOptionsMode := _root_.org.typelevel.sbt.tpolecat.CiMode"
      ) ++
      addCommandAlias(
        "tpolecatReleaseMode",
        "set ThisBuild / tpolecatOptionsMode := _root_.org.typelevel.sbt.tpolecat.ReleaseMode"
      )

  override def buildSettings: Seq[Setting[_]] = Seq(
    tpolecatDefaultOptionsMode := CiMode,
    tpolecatDevModeEnvVar      := "SBT_TPOLECAT_DEV",
    tpolecatCiModeEnvVar       := "SBT_TPOLECAT_CI",
    tpolecatReleaseModeEnvVar  := "SBT_TPOLECAT_RELEASE",
    tpolecatOptionsMode := {
      if (sys.env.contains(tpolecatReleaseModeEnvVar.value)) ReleaseMode
      else if (sys.env.contains(tpolecatCiModeEnvVar.value)) CiMode
      else if (sys.env.contains(tpolecatDevModeEnvVar.value)) DevMode
      else tpolecatDefaultOptionsMode.value
    },
    tpolecatDevModeOptions := ScalacOptions.default
  ) ++ commandAliases

  override def projectSettings: Seq[Setting[_]] = Seq(
    Def.derive(
      scalacOptions := {
        val pluginOptions   = tpolecatScalacOptions.value
        val pluginExcludes  = tpolecatExcludeOptions.value
        val selectedOptions = pluginOptions.diff(pluginExcludes)
        scalacOptionsFor(scalaVersion.value, selectedOptions)
      }
    ),
    Def.derive(
      tpolecatCiModeOptions := tpolecatDevModeOptions.value + ScalacOptions.fatalWarnings
    ),
    Def.derive(
      tpolecatReleaseModeOptions := tpolecatCiModeOptions.value + ScalacOptions.optimizerMethodLocal
    ),
    Def.derive(tpolecatScalacOptions := {
      tpolecatOptionsMode.value match {
        case DevMode     => tpolecatDevModeOptions.value
        case CiMode      => tpolecatCiModeOptions.value
        case ReleaseMode => tpolecatReleaseModeOptions.value
      }
    }),
    Compile / console / tpolecatExcludeOptions ++= ScalacOptions.defaultConsoleExclude,
    Test / console / tpolecatExcludeOptions ++= ScalacOptions.defaultConsoleExclude
  )

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    tpolecatExcludeOptions := Set.empty
  )
}
