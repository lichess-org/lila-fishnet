resolvers += Resolver.url("lila-maven-sbt", url("https://raw.githubusercontent.com/ornicar/lila-maven/master"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.typesafe.play" % "sbt-plugin"   % "2.8.2-lila_0.7")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.4.2")
