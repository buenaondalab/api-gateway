#!/bin/bash

./mvnw clean spring-boot:run -Dspring-boot.run.jvmArguments="-DEUREKA_USER=${EUREKA_USER} -DEUREKA_PASSWORD=${EUREKA_PASSWORD} -DSPRING_PROFILES_ACTIVE=dev"

