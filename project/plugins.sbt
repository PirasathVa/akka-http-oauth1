logLevel := Level.Warn

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.softwaremill.clippy" % "plugin-sbt" % "0.5.3")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.15")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.0.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
