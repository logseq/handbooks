build-ci:
	curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
	chmod +x install && ./install --dir .
	./bb build

nrepl:
	bb nrepl

clean:
	rm -rf outputs
