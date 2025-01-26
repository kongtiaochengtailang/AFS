#!/bin/bash

# model_types=("130m-hf" "370m-hf" "790m-hf" "1.4b-hf" "2.8b-hf")
model_types=("1.4b-hf" "2.8b-hf")
path_prefix=$1

python mamba_with_afs_eval.py \
    --model mamba_with_afs \
    --model_args pretrained=$path_prefix"790m-hf" \
    --tasks lambada_openai,hellaswag,arc_easy,arc_challenge,winogrande \
    --device cuda \
    --batch_size 8 \
    --output_path ./eval_results

# for type in ${model_types[@]}; do
#     case $type in
#         130m-hf)
#             bs=32
#             ;;
#         370m-hf)
#             bs=16
#             ;;
#         790m-hf)
#             bs=8
#             ;;
#         1.4b-hf)
#             bs=4
#             ;;
#         2.8b-hf)
#             bs=1
#             ;;
#         *) 
#             echo "unknown model type"
#             ;;
#     esac

#     python mamba_with_afs_eval.py \
#         --model mamba_with_afs \
#         --model_args pretrained=$path_prefix$type \
#         --tasks lambada_openai,hellaswag,arc_easy,arc_challenge,winogrande\
#         --device cuda \
#         --batch_size $bs \
#         --output_path ./eval_results
# done
        
        
# previous tasks: lambada_openai,hellaswag,arc_easy,arc_challenge,winogrande
