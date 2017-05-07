(ns hangr.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [hangr.events-test]))

(doo-tests 'hangr.events-test)
