#include "burner.h"
#include <iostream>

extern jint burn;

jint burn = 0;

JNIEXPORT void JNICALL Java_fk_prof_recorder_main_JniBurn_jniBurn(JNIEnv *env, jclass self) {
    std::cerr << "I got called\n";
    jint t = burn;
    while(true) {
        std::cerr << "I am still looping\n";
        t += (t * 0x5DEECE66DL + 0xBL + burn) & (0xFFFFFFFFFFFFL);
        if (t == 42) {
            burn += t;
        }
    }
}

