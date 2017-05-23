#ifndef SITE_RESOLVER_H
#define SITE_RESOLVER_H

#include "globals.hh"
#include "stacktraces.hh"
#include <map>
#include <memory>
#include <gelf.h>

namespace SiteResolver {
    class MethodListener {
    public:
        virtual void recordNewMethod(const jmethodID method_id, const char *file_name, const char *class_name, const char *method_name, const char* method_signature) = 0;
        virtual ~MethodListener() { }
    };

    typedef bool (*MethodInfoResolver)(const jmethodID method_id, jvmtiEnv* jvmti, MethodListener& listener);
    bool method_info(const jmethodID method_id, jvmtiEnv* jvmti, MethodListener& listener);

    typedef jint (*LineNoResolver)(jint bci, jmethodID method_id, jvmtiEnv* jvmti);
    jint line_no(jint bci, jmethodID method_id, jvmtiEnv* jvmti);

    class SymInfoError : std::runtime_error {
    public:
        SymInfoError(const char* msg);
        SymInfoError(const std::string& msg);
        virtual ~SymInfoError();
        static SymInfoError err(int err_num);
    };

    typedef NativeFrame Addr;

    struct MappedRegion {
        Addr start_;
        const std::string file_;
        std::map<Addr, std::string> symbols_;

        MappedRegion(Addr start, std::string file);

        ~MappedRegion();

        const void site_for(Addr addr, std::string& fn_name, Addr& pc_offset) const;

    private:
        SymInfoError section_error(Elf* elf, size_t shstrndx, Elf_Scn *scn, GElf_Shdr *shdr, const std::string& msg);

        void load_symbols(Elf* elf, GElf_Ehdr* ehdr, Elf_Scn* scn, Elf_Scn* xndxscn, GElf_Shdr* shdr);

        void load_elf(Elf* elf);
    };

    class SymInfo {
    private:
        typedef std::map<Addr, std::unique_ptr<MappedRegion>> Mapped;

        Mapped mapped; //start -> region

        typedef Mapped::const_iterator It;

        It region_for(Addr addr) const;

    public:
        SymInfo();

        void index(const char* path, Addr start);

        const std::string& file_for(Addr addr) const;

        void site_for(Addr addr, std::string& fn, Addr& pc_offset) const;

        void print_frame(Addr pc) const;
    };
}

#endif
