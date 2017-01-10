import sbt._
import Keys._

import com.typesafe.sbteclipse.plugin.EclipsePlugin._

import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

import no.vedaadata.sbtjavafx.JavaFXPlugin
import no.vedaadata.sbtjavafx.JavaFXPlugin.JFX

import sbtassembly.AssemblyPlugin.autoImport._

object BugPickerBuild extends Build {
	
	def getScalariformPreferences(dir: File) = PreferencesImporterExporter.loadPreferences(
		(file("./../../Scalariform Formatter Preferences.properties").getPath))
	
	lazy val buildSettings = Defaults.defaultSettings ++
		SbtScalariform.scalariformSettingsWithIt ++
		Seq(ScalariformKeys.preferences <<= baseDirectory.apply(getScalariformPreferences)) ++
		Seq(Defaults.itSettings : _*) ++
		Seq(
			EclipseKeys.configurations := Set(Compile, Test, IntegrationTest),
			EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
			EclipseKeys.withSource := true
		) ++
		Seq(libraryDependencies ++= Seq(
			"de.opal-project" %% "bugpicker-core" % "0.9.0-SNAPSHOT",
			"de.opal-project" %% "bytecode-disassembler" % "0.9.0-SNAPSHOT"
		)) ++
		Seq(resolvers ++= Seq(
			"Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
		))
	
	lazy val ui = Project(
		id = "BugPickerUI",
		base = file("ui"),
		settings = buildSettings ++
			JavaFXPlugin.jfxSettings ++ 
			Seq(
				JFX.mainClass := Option("org.opalj.bugpicker.BugPicker"),
				JFX.addJfxrtToClasspath := true
			) ++
			Seq(mainClass in (Compile, run) := Some("org.opalj.bugpicker.ui.BugPicker")) ++
			Seq(libraryDependencies += "org.scalafx"  %% "scalafx"   % "8.0.102-R11") ++
			Seq(
				resourceGenerators in Compile <+= Def.task {
					val versionFile = (baseDirectory in Compile).value / "target" / "scala-2.11" / "classes" / "org" / "opalj" / "bugpicker" / "version.txt"
					versionFile.getParentFile.mkdirs()
					IO.write(versionFile, (version in Compile).value)
					Seq(versionFile)
				}
			)
	)
}
