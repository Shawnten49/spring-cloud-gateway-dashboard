// Element Plus 组件 stub：组件测试聚焦业务逻辑（表单交互/加载/事件），不依赖真实组件运行时
export const elementStubs = {
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-alert': { template: '<div><slot /></div>' },
  'el-dialog': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>'
  },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-input-number': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-switch': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<button type="button" @click="$emit(\'update:modelValue\', !modelValue)" />'
  },
  'el-button': {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-table': { props: ['data'], template: '<div class="el-table"><slot /></div>' },
  'el-table-column': { props: ['prop', 'label'], template: '<span class="el-col">{{ label }}</span>' },
  'el-tag': { template: '<span><slot /></span>' }
}
