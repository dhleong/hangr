(ns ^{:author "Daniel Leong"
      :doc "Message utilities"}
  hangr.util.msg
  (:require [clojure.string :refer [split]]))

; url-regex is, by default, a global regex, which, 
;  unlike on jvm, has state. that doesn't play well
;  with clojure regex functions that expct stateless,
;  so we create a new RegExp instance based on the 
;  original, stripping the global flag (but keeping
;  the case insensitivity)
(def url-regex (js/RegExp. (.-source ((js/require "url-regex"))) "i"))

(defn- split-links
  [text]
  (let [regex nil]
    (loop [links (re-seq url-regex text)
          text text
          result []]
     (if-let [link (first links)]
       (let [before (subs text 
                          0
                          (.indexOf text link))]
         (recur
           (next links)
           (subs text (+ (.-length before)
                         (.-length link)))
           (concat
             result
             (if (> (.-length before) 0)
               [[:text before]
                [:link link]]
               [[:link link]]))))
       (if (> (.-length text) 0)
         (concat
           result
           [[:text text]])
         result)))))

(defn client-generated-id
  []
  ;; this is what hangupsjs does if you don't supply one:
  (.round js/Math
          (* (.random js/Math)
             (.pow js/Math 2, 32))))

(defn html->msg
  [html]
  (let [lines (split html #"(?:\<br[ /]*>)+")]
    ;; TODO formatting? <a>-style links?
    ;; TODO images?
    ;; TODO message type? (eg /me)
    (->> lines
         (mapcat
           (fn [line]
             (concat
               (split-links line)
               [[:newline]])))
         drop-last
         ;; insert a client-generated-id as the first thing
         (cons (client-generated-id)))))

(defn msg->event
  [msg]
  {:id (first msg)
   :client-generated-id (first msg)
   :chat_message
   {:message_content
    {:segment
     (map
       (fn [[type & args]]
         (case type
           :text {:type "TEXT"
                  :text (first args)}
           :link {:type "LINK"
                  :text (or (second args)
                            (first args))
                  :link_data
                  {:link_target (first args)}}
           :newline {:type "NEWLINE"}))
       (rest msg))}}})
