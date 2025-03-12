#!/bin/bash

VALIDATE_JAR="target/a1-validate-1.0.jar"

make
java -jar "$VALIDATE_JAR" 500 1 Transaction
java -jar "$VALIDATE_JAR" 500 1 TransactionNoBuffering
java -jar "$VALIDATE_JAR" 1000 1 Transaction
java -jar "$VALIDATE_JAR" 1000 1 TransactionNoBuffering
java -jar "$VALIDATE_JAR" 3000 1 Transaction
java -jar "$VALIDATE_JAR" 3000 1 TransactionNoBuffering
java -jar "$VALIDATE_JAR" 3000 5 Transaction
java -jar "$VALIDATE_JAR" 3000 5 TransactionNoBuffering
java -jar "$VALIDATE_JAR" 3000 10 Transaction
java -jar "$VALIDATE_JAR" 3000 10 TransactionNoBuffering