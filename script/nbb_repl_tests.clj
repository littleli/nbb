(ns nbb-repl-tests
  (:require
   [babashka.process :as p :refer [process]]
   [babashka.wait :refer [wait-for-port]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is]])
  (:import [java.net Socket]))

(defn repl [input]
  (-> (process ["node" "out/nbb_main.js"]
               {:out :string
                :in input
                :err :inherit})
      p/check))

(deftest repl-test
  (is (str/includes? (:out (repl "(+ 1 2 3)")) "6\n"))
  (is (str/includes? (:out (repl "(+\n 1\n 2\n 3\n)")) "6\n"))
  (is (str/includes? (:out (repl "(ns foo) (= \"foo\" (str *ns*))")) "true"))
  (is (str/includes?
       (:out (repl "(ns foo (:require [\"fs\" :as fs])) (fs/existsSync \"README.md\")"))
       "true\n"))
  (is (str/includes?
       (:out (repl "(js/Promise.resolve 10)"))
       "Promise")))

(defn socket-repl
  ([input] (socket-repl input nil))
  ([input match]
   (let [p (process ["node" "out/nbb_main.js" "socket-repl" ":port" "1337"]
                    {:inherit true})]
     (wait-for-port "localhost" 1337)
     (with-open [socket (Socket. "127.0.0.1" 1337)
                 in (.getInputStream socket)
                 in (java.io.PushbackInputStream. in)
                 os (.getOutputStream socket)]
       (binding [*out* (io/writer os)]
         (println input))
       (let [output (atom "")
             res (binding [*in* (io/reader in)]
                   (if match
                     (loop []
                       (when-let [s (read-line)]
                         (swap! output str s)
                         (if (str/includes? @output match)
                           @output
                           (recur))))
                     (read-line)))]
         (binding [*out* (io/writer os)]
           (println "(js/process.exit 0)"))
         (p/check p)
         {:out (str res "\n")})))))

(deftest socket-repl-test
  (is (str/includes? (:out (socket-repl "(+ 1 2 3)")) "6\n"))
  (is (str/includes? (:out (socket-repl "(+\n 1\n 2\n 3\n)")) "6\n"))
  (is (str/includes? (:out (socket-repl "(ns foo) (= \"foo\" (str *ns*))" "true")) "true"))
  (is (str/includes?
       (:out (socket-repl "(ns foo (:require [\"fs\" :as fs])) (fs/existsSync \"README.md\")"
                          "true"))
       "true\n"))
  (is (str/includes?
       (:out (socket-repl "(js/Promise.resolve 10)"))
       "Promise")))

(defn -main [& _]
  (let [{:keys [:error :fail]} (t/run-tests 'nbb-repl-tests)]
    (when (pos? (+ error fail))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))
