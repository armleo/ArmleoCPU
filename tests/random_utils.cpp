
void utils_init() {
    srand (RANDOM_SEED);
    

}


uint32_t rand1() {
    return rand() & 0b1;
}


uint32_t rand2() {
    return rand() & 0b11;
}


uint32_t rand3() {
    return rand() & 0b111;
}


uint32_t rand8() {
    return rand() & 0xFF;
}


uint32_t rand12() {
    return rand() & 0xFFF;
}
