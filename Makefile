install-babashka:
	curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
	chmod +x install && ./install --dir .
	export PATH=$PATH:$(pwd)

dev:clean
	bb dev

build:clean
	bb build

clean:
	rm -rf outputs