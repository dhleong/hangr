(ns ^:figwheel-no-load hangr.dev
  (:require [hangr.core :as core]
            [devtools.core :as devtools]))

;; -- Debugging aids ----------------------------------------------------------
(devtools/install! [:formatters :hints])       ;; we love https://github.com/binaryage/cljs-devtools

(core/init!)
