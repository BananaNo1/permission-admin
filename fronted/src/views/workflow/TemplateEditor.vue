<script setup lang="ts">
import {ref, watch} from 'vue'
import {api} from '@/api/http'

type Step = {name:string; mode:string; approverIds:number[]; minAmount:number|null; returnTo:string}
type Template = {code:string; name:string; revision:number; publishedVersion:number|null; dirty:boolean; steps:Step[]}
const props = defineProps<{business:string; template:Template|null; edit:boolean; publish:boolean}>()
const emit = defineEmits<{saved:[code:string]}>()
const code = ref(''), name = ref(''), message = ref(''), busy = ref(false)
const steps = ref<Array<Omit<Step,'approverIds'|'minAmount'> & {ids:string; minAmount:string}>>([])
const modified = ref(false)
function add() {
    steps.value.push({name:'',mode:'SINGLE',ids:'',minAmount:'',returnTo:'APPLICANT'})
    modified.value = true
}
watch(() => [props.business, props.template], () => {
    const item = props.template
    code.value = item?.code ?? ''
    name.value = item?.name ?? ''
    steps.value = item?.steps.map(({approverIds,...step}) => ({...step,minAmount:String(step.minAmount ?? ''),ids:approverIds.join(', ')})) ?? []
    if (!item) add()
    modified.value = false
    message.value = ''
}, {immediate:true})
function allowedFirst(step:typeof steps.value[number] | undefined) {
    if (!step || (!step.minAmount && step.returnTo==='APPLICANT')) return true
    message.value = '首节点必须无金额条件且退回申请人，请先修改即将排在首位的节点'
    return false
}
function remove(index:number) {
    if (index===0 && !allowedFirst(steps.value[1])) return
    steps.value.splice(index,1)
    modified.value = true
    message.value = ''
}
function move(index:number, delta:number) {
    if (index+delta===0 && !allowedFirst(steps.value[index])) return
    if (index===0 && !allowedFirst(steps.value[index+delta])) return
    message.value = ''
    const [step] = steps.value.splice(index,1)
    steps.value.splice(index+delta,0,step)
    modified.value = true
}
async function submit(publishing:boolean) {
    if (busy.value) return
    const key = code.value.trim()
    const path = '/workflow/businesses/'+encodeURIComponent(props.business)+'/templates/'+encodeURIComponent(key)
    const revision = props.template?.revision ?? 0
    message.value = ''
    busy.value = true
    try {
        if (publishing) {
            await api(path+'/publish','POST',{revision})
        } else {
            if (!key || !name.value.trim() || !steps.value.length) throw Error('请填写模板编码、名称，并添加审批节点')
            if (steps.value.length>30) throw Error('最多可配置 30 个审批节点')
            if (!allowedFirst(steps.value[0])) return
            const payload = steps.value.map(({ids,...step},i) => {
                const tokens = ids.trim().split(/[\s,，]+/)
                const approverIds = tokens.map(Number)
                if (!step.name.trim() || tokens.some(id => !/^[1-9]\d*$/.test(id)) || approverIds.some(id => !Number.isSafeInteger(id))
                    || new Set(approverIds).size !== approverIds.length || (step.mode==='SINGLE' ? approverIds.length!==1 : approverIds.length<2 || approverIds.length>50)) {
                    throw Error('请检查第 '+(i+1)+' 个节点的名称和审批人 ID')
                }
                const raw = step.minAmount.trim()
                const amount = raw ? Number(raw) : null
                if (amount !== null) {
                    const decimal = /^(\d{1,16})(?:\.(\d{1,2}))?$/.exec(raw)
                    const canonical = decimal && (decimal[1].replace(/^0+(?=\d)/,'') + '.' + (decimal[2] ?? '').padEnd(2,'0'))
                    if (!canonical || amount.toFixed(2)!==canonical || String(amount)!==canonical.replace(/0+$/,'').replace(/\.$/,'')) {
                        throw Error('第 '+(i+1)+' 个节点金额须为可精确表示的非负金额，最多两位小数')
                    }
                }
                return {...step,name:step.name.trim(),minAmount:amount,approverIds}
            })
            await api(path,'PUT',{name:name.value.trim(),revision,steps:payload})
        }
        emit('saved',key)
    } catch (error) {
        message.value = error instanceof Error ? error.message : '操作失败'
    } finally { busy.value = false }
}
</script>

<template>
<form class="editor" @submit.prevent="submit(false)" @input="modified=true">
  <p class="state" v-if="template">已发布版本 {{ template.publishedVersion || '暂无' }} · {{ modified ? '有未保存修改' : template.dirty ? '草稿待发布' : '草稿已发布' }}</p>
  <fieldset :disabled="!edit || busy">
    <label>模板编码<input v-model="code" required :readonly="!!template"></label>
    <label>模板名称<input v-model="name" required></label>
    <article v-for="(step,i) in steps" :key="i">
      <strong>节点 {{ i+1 }}</strong>
      <label>节点名称<input v-model="step.name" required></label>
      <label>审批模式<select v-model="step.mode"><option value="SINGLE">单人审批</option><option value="ANY">或签</option><option value="ALL">会签</option><option value="SEQUENTIAL">依次审批</option></select></label>
      <label>审批人 ID<input v-model="step.ids" required placeholder="多个 ID 按审批顺序用逗号分隔"></label>
      <label>最低金额<input v-model="step.minAmount" inputmode="decimal" :disabled="i===0" placeholder="不限"></label>
      <label>退回位置<select v-model="step.returnTo"><option value="APPLICANT">申请人</option><option value="PREVIOUS" :disabled="i===0">上一节点</option></select></label>
      <div v-if="edit"><button type="button" :disabled="i===0" @click="move(i,-1)">上移</button> <button type="button" :disabled="i===steps.length-1" @click="move(i,1)">下移</button> <button type="button" @click="remove(i)">删除节点</button></div>
    </article>
    <button v-if="edit" type="button" :disabled="steps.length>=30" @click="add">添加节点</button>
  </fieldset>
  <div class="actions"><button v-if="edit" :disabled="busy">保存草稿</button> <button v-if="publish && template" type="button" :disabled="busy || modified || !template.dirty" @click="submit(true)">发布版本</button></div>
  <p v-if="message" role="alert">{{ message }}</p>
</form>
</template>

<style scoped>
.editor, fieldset {display:grid;gap:14px}
fieldset {border:0;padding:0;margin:0;min-width:0}
article {display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;background:#f5f7fb;border:1px solid #d9e2ed;padding:14px;border-radius:8px}
article strong, article div {grid-column:1/-1}
label {display:grid;gap:5px}
input,select,button {padding:7px 10px;border:1px solid #bdcbdc;background:white;border-radius:5px;font:inherit}
button {width:fit-content;cursor:pointer;color:#245baf}
.state {margin:0;color:#465978}
.actions {display:flex;gap:10px;border-top:1px solid #d9e2ed;padding-top:14px}
.actions button:first-child {background:#245baf;color:white;border-color:#245baf}
button:disabled {opacity:.5;cursor:default}
[role=alert] {color:#ab2537}
</style>
