(ns cloxy.server
  [:use cloxy.core])

(defn -main [& [port]]
  (let [p (or port "7070")]
	  (start-server (read-string p))
  ))
