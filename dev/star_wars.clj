(ns star-wars
  (:require [com.brunobonacci.mulog :as u]))

(defonce stars ["chewbacca" "bb-8" "boba-fett" "princess-leia" "r2-d2" "c-3po"
                "qui-gon-jinn" "han-solo" "darth-vader" "luke-skywalker"])

(defn sleep []
  (Thread/sleep (rand-int 1000)))

(defn play [episode]
  ;; this is to emulate a different app / namespace calling the function
  (u/with-context {:app-name (rand-nth stars)}
    (episode)))

;; µ/log'wise these would live across different apps or namespaces
(defn the-rise-of-skywalker [] (sleep)) ;; functions do things.. some functions sleep
(defn the-last-jedi [] (sleep))
(defn the-force-awakens [] (sleep))
(defn return-of-the-jedi [] (sleep))
(defn the-empire-strikes-back [] (sleep))
(defn a-new-hope [] (sleep))

(defn rogue-one [] (sleep))
(defn solo [] (sleep))

(defn one-offs []
  (play solo)
  (play rogue-one))

(defn revenge-of-the-sith [] (sleep))
(defn attack-of-the-clones [] (sleep))
(defn the-phantom-menace [] (sleep))

(defn binge []
  (u/set-global-context! {:app-name "binging"})
  (doseq [episode [the-phantom-menace
                   attack-of-the-clones
                   revenge-of-the-sith
                   a-new-hope
                   the-empire-strikes-back
                   one-offs
                   return-of-the-jedi
                   the-force-awakens
                   the-last-jedi
                   the-rise-of-skywalker]]
    (play episode)))
