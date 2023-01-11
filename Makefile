install-babashka:
	curl -s https://raw.githubusercontent.com/babashka/babashka/master/install

dev:clean
	bb dev

build:clean
	bb build

clean:
	rm -rf outputs