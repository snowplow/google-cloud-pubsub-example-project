/*
 * Copyright (c) 2017 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
import sbt._
import Keys._

object BuildSettings {

  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization  := "com.snowplowanalytics",
    version       := "0.1.0",
    description   := "A Dataflow Streaming job reading events from Cloud Pub/Sub and writing event counts to Bigtable",
    scalaVersion  := "2.11.8",
    scalacOptions :=  Seq("-deprecation", "-encoding", "utf8",
                          "-feature", "-target:jvm-1.8"),
    scalacOptions in Test :=  Seq("-Yrangepos"),
    resolvers     ++= Dependencies.resolutionRepos,

    javaOptions in run += "-Xbootclasspath/p:/home/colobas/.ivy2/cache/org.mortbay.jetty.alpn/alpn-boot/jars/alpn-boot-8.1.5.v20150921.jar",
    fork in run := true
  )

  // Makes our SBT app settings available from within the app
  lazy val scalifySettings = Seq(sourceGenerators in Compile <+= (sourceManaged in Compile, version, name, organization) map { (d, v, n, o) =>
    val file = d / "settings.scala"
    IO.write(file, """package com.snowplowanalytics.dataflow.streaming.generated
      |object Settings {
      |  val organization = "%s"
      |  val version = "%s"
      |  val name = "%s"
      |}
      |""".stripMargin.format(o, v, n))
    Seq(file)
  })

  // sbt-assembly settings for building a fat jar
  import sbtassembly.Plugin._
  import AssemblyKeys._
  lazy val sbtAssemblySettings = assemblySettings ++ Seq(

    // Simpler jar name
    jarName in assembly := {
      name.value + "-" + version.value + ".jar"
    },

    // Drop these jars
    excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
      val excludes = Set(
        "junit-4.5.jar", // We shouldn't need JUnit
        "jsp-api-2.1-6.1.14.jar",
        "jsp-2.1-6.1.14.jar",
        "jasper-compiler-5.5.12.jar",
        "minlog-1.2.jar", // Otherwise causes conflicts with Kyro (which bundles it)
        "janino-2.5.16.jar", // Janino includes a broken signature, and is not needed anyway
        "commons-beanutils-core-1.8.0.jar", // Clash with each other and with commons-collections
        "commons-beanutils-1.7.0.jar",      // "
        "hadoop-tools-0.20.2.jar",
        "guava-14.0.1.jar", // conflict spark-network-common_2.10-1.3.0.jar
        "jcl-over-slf4j-1.7.10.jar", //conflict commons-logging-1.1.3.jar
        "hadoop-yarn-api-2.2.0.jar",
        "bigtable-protos-0.3.0.jar", //conflict grpc-core-proto-0.0.3.jar
        "datastore-v1-protos-1.0.1.jar", //conflict grpc-core-proto-0.0.3.jar
        "appengine-api-1.0-sdk-1.9.34.jar", 
        "guava-jdk5-17.0.jar",
        "netty-all-4.0.23.Final.jar" //conflict with many netty jars imported independently
//        "hbase-client-1.0.2.jar"        
      )
      cp filter { jar => excludes(jar.data.getName) }
    },

    mergeStrategy in assembly <<= (mergeStrategy in assembly) {
      (old) => {
        case x if x.contains("UnusedStubClass.class") => MergeStrategy.first
        case x if x.endsWith("project.clj") => MergeStrategy.discard // Leiningen build files
        case x if x.startsWith("META-INF") => MergeStrategy.discard // More bumf
        case x if x.endsWith(".html") => MergeStrategy.discard

        case x if x.startsWith("com/google/cloud/bigtable/") => MergeStrategy.last
        case x if x.startsWith("com/google/longrunning/") => MergeStrategy.last
        case x if x.startsWith("com/google/rpc/") => MergeStrategy.last
        case x if x.startsWith("com/google/type/") => MergeStrategy.last
        case x if x.startsWith("google/protobuf/") => MergeStrategy.last
       
        case x => old(x)
      }
    }
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++ sbtAssemblySettings
}
