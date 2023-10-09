<!-- Copyright (c) Microsoft Corporation. All rights reserved. -->

# Building libjitsi
The project is built using Apache Ant tool and Apache Ivy as a dependency manager.

Additional 3rd party Java libraries are required in order to build the project. There are 2 options how they can be provided for the build.

## Using Maven repository
The dependencies can be downloaded from a Maven repository:
1. Create `ivysettings.xml` file with definitions of dependency resolvers. E.g. to download artefacts from Maven Central it can look like:
    ```ivysettings.xml
    <ivy-settings>
        <settings defaultResolver="chain-resolver" />
        <resolvers>
            <chain name="chain-resolver" returnFirst="true">
                <!-- First check local folder -->
                <filesystem name="libraries">
                    <artifact pattern="${project.basedir}/lib/[artifact].[ext]"/>
                </filesystem>
                <ibiblio name="maven" m2compatible="true" root="https://repo1.maven.org/maven2/" />
            </chain>
        </resolvers>
    </ivy-settings>
    ```
2. Not all required dependencies available in Maven. Download, build and put to `lib` folder the following libraries:
   - fmj.jar - built from source https://github.com/microsoft/MaXUC-FMJ-Fork/
3. Run `ant make` command which will download dependencies and build `libjitsi.jar` as an output artefact.

## Offline build
Another way to build without Maven is to manually put libraries files:
1. Provide required libraries in `lib` folder (check `ivy.xml` for JAR versions):
   - bcpkix-jdk18on.jar - *Bouncy Castle PKIX, CMS, EAC, TSP, PKCS, OCSP, CMP, and CRMF APIs*
   - bcprov-jdk18on.jar - *Bouncy Castle Provider*
   - bcutil-jdk18on.jar - *Bouncy Castle ASN.1 Extension and Utility APIs*
   - commons-lang3.jar - *Apache Commons Lang*
   - commons-math3.jar - *Apache Commons Math*
   - guava.jar - *Guava: Google Core Libraries For Java*
   - ffmpeg.jar - *JavaCPP Presets For FFmpeg*
   - javacpp.jar - *JavaCPP*
   - json-simple.jar - *JSON.simple*
   - osgi.core.jar - *OSGi Core (OSGi Core Release 8, Interfaces and Classes for use in compiling bundles)*
   - sdes4j.jar - *SDES (RFC4568) implementation for Java*
   - jain-sip-ri.jar - *JAIN-SIP Reference Implementation*
2. Download, build and put to `lib` folder the following libraries:
   - fmj.jar - built from source https://github.com/microsoft/MaXUC-FMJ-Fork/
3. Run `ant make -DofflineBuild=true` to build `libjitsi.jar` using provided JARs.
