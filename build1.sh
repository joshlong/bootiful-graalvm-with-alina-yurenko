#!/usr/bin/env bash

./mvnw  clean native:compile -Pnative  -e  && ./target/basics 
