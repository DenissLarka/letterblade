#!/usr/bin/env pwsh

# Compile and run Letterblade from this clone (mvn compile + javafx:run). JavaFX comes from the JDK on
# PATH, so that JDK must be a JavaFX-bundled build at the pom's java.version (Azul Zulu FX).
# The first arg (if any) is a .msg file to open at start-up.

$pom = Join-Path $PSScriptRoot 'pom.xml'
if ($args.Count -gt 0) {
    $msg = (Resolve-Path $args[0]).Path
    mvn -f $pom compile javafx:run "-Djavafx.args=`"$msg`""
} else {
    mvn -f $pom compile javafx:run
}
