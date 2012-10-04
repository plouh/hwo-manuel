(ns cloxy.core
  [:use lamina.core aleph.formats aleph.http aleph.tcp])

(def channels (atom #{}))

(defn register [msg]
  (println msg)
  (doall
   (for [k @channels]
     (if (closed? k)
       (swap! channels disj k)
       (enqueue k msg)))))

(defn event-loop [channel opt]
  (swap! channels conj channel)
  (receive-all channel register))

(defn start-server [port]
  (start-http-server event-loop {:port port :websocket true}))
