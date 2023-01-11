install-babashka:
	curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
	chmod +x install
	./install

dev:clean
	bb dev

build:clean
	bb build

clean:
	rm -rf outputs