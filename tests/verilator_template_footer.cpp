
    } catch(runtime_error e) {
        cout << "%Error:" << e.what() << endl;
        next_cycle();
        next_cycle();
        error_happened = 1;

    }
    TOP->final();

    #ifdef TRACE
        m_trace->close();
        m_trace = NULL;
    #endif

#if VM_COVERAGE
    Verilated::mkdir("logs");
    VerilatedCov::write("logs/coverage.dat");
#endif

    // Destroy model
    delete TOP; TOP = NULL;

    // Fin
    if(error_happened)
        return -1;
    return 0;
}