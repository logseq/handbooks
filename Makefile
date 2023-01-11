build-ci:
	curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
	chmod +x install && ./install --dir .
	./bb build

build:clean
	bb build

dev:clean
	bb dev

nrepl:
	bb nrepl

clean:
	rm -rf outputs