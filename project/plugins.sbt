addSbtPlugin("org.scalameta"  % "sbt-scalafmt"  % "2.6.2")
addSbtPlugin("org.scoverage"  % "sbt-scoverage" % "2.4.4")
addSbtPlugin("com.github.sbt" % "sbt-header"    % "5.11.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.7.1")
// sbt-updates is often also installed as a global plugin (~/.sbt/1.0/plugins); don't fail on an older global version
libraryDependencySchemes += "com.timushev.sbt" % "sbt-updates" % VersionScheme.Always
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.13.0")
