(ns ^{:author "Daniel Leong"
      :doc "chrome-runner"}
  hangr.chrome-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [hangr.events-test]
            [hangr.util.msg-test]))

(doo-tests 'hangr.events-test
           'hangr.util.msg-test)
