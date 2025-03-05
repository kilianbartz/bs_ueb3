# Simple makefile for Maven project

# Default target
all: package

# Target to run mvn clean package
package:
	mvn clean package
	cp target/a1-1.0.jar ../a2/v1.jar

# Clean target (optional, as Maven handles cleaning)
clean:
	mvn clean

# Help target
help:
	@echo "Available targets:"
	@echo "  all      - Default target, same as 'package'"
	@echo "  package  - Run 'mvn clean package'"
	@echo "  clean    - Run 'mvn clean'"
