# java-video-quality
A Java program to evaluate the quality of videos from AR.Drone 1.0 and GoPro HERO3. 

## Metrics used

* **AR.Drone 1.0**
  * Received frames
  * Lost packets
  * Sent packets

* **GoPro HERO3**
  * Received frames
  * Lost frames

## Prerequisites

* Maven

## Installation

First, go to the directory where the `pom.xml` file is located, then

```
mvn package
cd /target
java -jar java-video-quality-1.0.jar
```

## Built With

* [Commons CLI](http://commons.apache.org/proper/commons-cli/) - Command Line arguments parser
* [Maven](https://maven.apache.org/) - Dependency Management

## Authors

* **Licia Amichi** - *Student at Pierre and Marie Curie University*
* **Omar Barguache** - *Student at Pierre and Marie Curie University*
* **Frédéric Chen** - *Student at Pierre and Marie Curie University*
* **Ziang Chen** - *Student at Pierre and Marie Curie University*
