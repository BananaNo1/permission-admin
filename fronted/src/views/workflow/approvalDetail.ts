export const labels: Record<string, string> = {
  PENDING: '审批中', RETURNED: '已退回', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消',
  APPROVE: '通过', REJECT: '驳回', RETURN: '退回', RESUBMIT: '重新提交',
  TRANSFER: '转办', WITHDRAW: '撤回', TERMINATE: '管理员终止', ACTIVE: '在途', EXCEPTION: '引擎作业异常',
}
export const dateTime = (value: string) => new Date(value).toLocaleString('zh-CN')
