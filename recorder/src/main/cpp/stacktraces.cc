#include "stacktraces.hh"

std::uint32_t Stacktraces::fill_backtrace(NativeFrame* buff, std::uint32_t capacity) {//TODO: write me 3 tests { (capacity > stack), (stack > capacity) and (stack == capacity) }
    std::uint64_t rbp, rpc;
    asm("movq %%rbp, %%rax;"
        "movq %%rax, %0;"
        "lea (%%rip), %%rax;"
        "movq %%rax, %1;"
        : "=r"(rbp), "=r"(rpc)
        :);

    //not adding current PC, because we are anyway not interested in showing ourselves on the backtrace
    std::uint32_t i = 0;
    while ((capacity - i) > 0) {
        rpc = *reinterpret_cast<std::uint64_t*>(rbp + 8);
        //if (rpc == 0) break;
        buff[i] = rpc;
        rbp = *reinterpret_cast<std::uint64_t*>(rbp);
        if (rbp == 0) break;
        i++;
    }
    return i;
}

std::uint32_t Stacktraces::calculate_max_stack_depth(std::uint32_t _max_stack_depth) {
    return (_max_stack_depth > 0 && _max_stack_depth < (MAX_FRAMES_TO_CAPTURE - 1)) ? _max_stack_depth : DEFAULT_MAX_FRAMES_TO_CAPTURE;
}
