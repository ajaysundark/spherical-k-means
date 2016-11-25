preprocess:
	python reuters.py

kmeans:
	javac sphkmeans.java

default: kmeans
