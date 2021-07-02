.PHONY: clean jar tag outdated install deploy tree repl

clean:
	rm -rf target

jar: tag
	clojure -A:jar

outdated:
	clojure -M:outdated

tag:
	clojure -A:tag

install: jar
	clojure -A:install

deploy: jar
	clojure -A:deploy

tree:
	mvn dependency:tree

repl:
	clojure -A:dev -A:repl
