#include <jni.h>
#include "llama.h"

#include <algorithm>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
struct SessionState {
    llama_context* ctx;
    std::vector<llama_token> last_tokens;
    int32_t n_past = 0;
    float temperature;
    float min_p;
    float repetition_penalty;
    bool finished = false;
    int32_t threads = 1;
};

std::string toStdString(JNIEnv* env, jstring value) {
    const char* utfChars = env->GetStringUTFChars(value, nullptr);
    std::string result(utfChars);
    env->ReleaseStringUTFChars(value, utfChars);
    return result;
}

std::vector<llama_token> tokenize(llama_context* ctx, const std::string& prompt) {
    llama_model* model = llama_get_model(ctx);
    std::vector<llama_token> tokens(prompt.size() + 4);
    int count = llama_tokenize(model, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
    if (count < 0) {
        tokens.resize(-count);
        count = llama_tokenize(model, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, true);
    }
    if (count < 0) {
        throw std::runtime_error("Failed to tokenize prompt");
    }
    tokens.resize(count);
    return tokens;
}

void evaluate(llama_context* ctx, const std::vector<llama_token>& tokens, int32_t n_past, int32_t threads) {
    const int32_t batch_size = 512;
    int32_t total = tokens.size();
    for (int32_t i = 0; i < total; i += batch_size) {
        int32_t current = std::min(batch_size, total - i);
        if (llama_eval(ctx, tokens.data() + i, current, n_past, threads)) {
            throw std::runtime_error("llama_eval failed");
        }
        n_past += current;
    }
}

std::string tokenToPiece(llama_context* ctx, llama_token token) {
    std::string piece;
    piece.resize(256);
    const int32_t result = llama_token_to_piece(ctx, token, piece.data(), piece.size());
    if (result < 0) {
        throw std::runtime_error("Failed to convert token");
    }
    piece.resize(result);
    return piece;
}

}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_initBackend(JNIEnv*, jobject) {
    llama_backend_init();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_loadModel(JNIEnv* env, jobject, jstring path, jint nCtx) {
    std::string filePath = toStdString(env, path);
    llama_model_params params = llama_model_default_params();
    params.use_mmap = true;
    params.use_mlock = false;
    params.n_gpu_layers = 0;
    llama_model* model = llama_load_model_from_file(filePath.c_str(), params);
    if (!model) {
        throw std::runtime_error("Failed to load model");
    }
    (void)nCtx;
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_createContext(JNIEnv*, jobject, jlong modelHandle, jint nThreads, jint nCtx) {
    auto* model = reinterpret_cast<llama_model*>(modelHandle);
    llama_context_params params = llama_context_default_params();
    params.n_ctx = nCtx;
    params.n_threads = nThreads;
    params.n_threads_batch = nThreads;
    llama_context* ctx = llama_new_context_with_model(model, params);
    if (!ctx) {
        throw std::runtime_error("Failed to create llama context");
    }
    llama_kv_cache_clear(ctx);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_beginCompletion(JNIEnv* env, jobject, jlong ctxHandle, jstring prompt, jfloat temperature, jfloat minP, jfloat repetitionPenalty, jint nThreads) {
    auto* ctx = reinterpret_cast<llama_context*>(ctxHandle);
    const std::string promptStr = toStdString(env, prompt);
    const int32_t threads = nThreads;
    std::vector<llama_token> tokens = tokenize(ctx, promptStr);
    evaluate(ctx, tokens, 0, threads);
    auto* session = new SessionState();
    session->ctx = ctx;
    session->n_past = tokens.size();
    session->temperature = std::max(0.01f, temperature);
    session->min_p = std::max(0.01f, minP);
    session->repetition_penalty = repetitionPenalty;
    session->last_tokens = tokens;
    if (session->last_tokens.size() > 128) {
        session->last_tokens.erase(session->last_tokens.begin(), session->last_tokens.end() - 128);
    }
    session->finished = false;
    session->threads = std::max(1, threads);
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_pullToken(JNIEnv* env, jobject, jlong sessionHandle) {
    auto* session = reinterpret_cast<SessionState*>(sessionHandle);
    if (!session || session->finished) {
        return nullptr;
    }
    llama_context* ctx = session->ctx;
    llama_model* model = llama_get_model(ctx);
    const int32_t maxContext = llama_n_ctx(ctx);
    if (session->n_past >= maxContext - 1) {
        session->finished = true;
        return nullptr;
    }
    const int32_t vocab = llama_n_vocab(model);

    const float* logits = llama_get_logits(ctx);
    std::vector<llama_token_data> candidates;
    candidates.reserve(vocab);
    for (int i = 0; i < vocab; ++i) {
        candidates.emplace_back(i, logits[i], 0.0f);
    }
    llama_token_data_array candidatesArray = { candidates.data(), candidates.size(), false };

    if (!session->last_tokens.empty()) {
        llama_sample_repetition_penalty(ctx, &candidatesArray, session->last_tokens.data(), session->last_tokens.size(), session->repetition_penalty);
    }
    llama_sample_min_p(ctx, &candidatesArray, session->min_p, 1);
    llama_sample_temperature(ctx, &candidatesArray, session->temperature);

    llama_token token = llama_sample_token(ctx, &candidatesArray);
    if (token == llama_token_eos(model)) {
        session->finished = true;
        return nullptr;
    }
    session->last_tokens.push_back(token);
    if (session->last_tokens.size() > 128) {
        session->last_tokens.erase(session->last_tokens.begin());
    }

    std::string piece = tokenToPiece(ctx, token);
    std::vector<llama_token> next = { token };
    evaluate(ctx, next, session->n_past, session->threads);
    session->n_past += next.size();
    return env->NewStringUTF(piece.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_releaseSession(JNIEnv*, jobject, jlong sessionHandle) {
    auto* session = reinterpret_cast<SessionState*>(sessionHandle);
    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_releaseContext(JNIEnv*, jobject, jlong ctxHandle) {
    auto* ctx = reinterpret_cast<llama_context*>(ctxHandle);
    if (ctx) {
        llama_free(ctx);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_unloadModel(JNIEnv*, jobject, jlong modelHandle) {
    auto* model = reinterpret_cast<llama_model*>(modelHandle);
    if (model) {
        llama_free_model(model);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lilspeaker_features_llm_LlamaBridge_shutdown(JNIEnv*, jobject) {
    llama_backend_free();
}
