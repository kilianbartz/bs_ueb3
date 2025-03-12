# Simple makefile for Maven project

# Define source and destination paths
SOURCE_JAR = target/a1-1.0.jar
DEST_JAR = ../a2/v1.jar
VALIDATE_JAR = target/a1-validate-1.0.jar
VALIDATE_ITERATIONS = 3000
VALIDATE_NO_FILES = 1
VALIDATE_METHOD = Transaction

# Default target
all: package

# Target to run mvn clean package
package:
	mvn clean package
	cp $(SOURCE_JAR) $(DEST_JAR)

# Clean target (optional, as Maven handles cleaning)
clean:
	mvn clean

# Target to validate the package
cleanvalidate: package
	./zfs_cleanup.sh
	java -jar $(VALIDATE_JAR) $(VALIDATE_ITERATIONS) $(VALIDATE_NO_FILES) $(VALIDATE_METHOD)

validate: package
	java -jar $(VALIDATE_JAR) $(VALIDATE_ITERATIONS) $(VALIDATE_NO_FILES) $(VALIDATE_METHOD)

# Help target
help:
	@echo "Available targets:"
	@echo "  all      - Default target, same as 'package'"
	@echo "  package  - Run 'mvn clean package'"
	@echo "  clean    - Run 'mvn clean'"
