(ns cemerick.nrepl.main
  (:gen-class)
  (:require [cemerick.nrepl :as repl]))

(defn- run-repl
  [port]
  (let [connection (repl/connect "localhost" port)
        {:keys [major minor incremental qualifier]} *clojure-version*]
    (println "network-repl")
    (print (str "Clojure " major \. minor \. incremental))
    (if (seq qualifier)
      (println (str \- qualifier))
      (println))
    (loop [ns "user"]
      (print (str ns "=> "))
      (flush)
      ((:send connection) (pr-str (read)))
      (let [{:keys [out ns value]} ((:receive connection))]
        (when (seq out) (print out))
        (recur ns)))))

(defn- split-args
  [args]
  (loop [[arg & rem-args :as args] args
         options {}]
    (if-not (and arg (re-matches #"--.*" arg))
      [options args]
      (if (#{"--repl"} arg)
            (recur rem-args
              (assoc options arg true))
            (recur (rest rem-args)
              (assoc options arg (first rem-args)))))))

(defn -main
  [& args]
  (let [[ssocket _] (repl/start-server)
        [options args] (split-args args)]
    (when-let [ack-port (options "--ack")]
      (binding [*out* *err*]
        (println (format "ack'ing my port %d to other server running on port %s"
                   (.getLocalPort ssocket) ack-port)
          (:status (#'cemerick.nrepl/send-ack (.getLocalPort ssocket) (Integer/parseInt ack-port))))))
    (if (options "--repl")
      (run-repl (.getLocalPort ssocket))
      ; need to hold process open with a non-daemon thread -- this should end up being super-temporary
      (Thread/sleep Long/MAX_VALUE))))