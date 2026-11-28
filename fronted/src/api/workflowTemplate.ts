import { api } from './http'
import type { DiagramLayout, Reviewer, ReviewerRole, TemplateDraft, TemplateStep, TemplateVersion, TemplateVersionDetail, WorkflowRelease } from '@/types/workflow'

const templatesPath = (business: string) => '/workflow/businesses/' + encodeURIComponent(business) + '/templates'
const templatePath = (business: string, code: string) => templatesPath(business) + '/' + encodeURIComponent(code)

export function listWorkflowTemplates(business: string) {
    return api<TemplateDraft[]>(templatesPath(business))
}

export function listPublishedTemplates(business: string) {
    return api<WorkflowRelease[]>(templatesPath(business) + '/published')
}

export function listTemplateReviewerRoles(business: string) {
    return api<ReviewerRole[]>(templatesPath(business) + '/reviewer-roles')
}

export function listTemplateReviewers(business: string, filters: { keyword: string } | { ids: number[] }) {
    const query = new URLSearchParams('ids' in filters ? { ids: filters.ids.join(',') } : filters)
    return api<Reviewer[]>(templatesPath(business) + '/reviewers?' + query)
}

export function saveTemplateDraft(business: string, code: string,
    body: { name: string; revision: number; steps: TemplateStep[]; diagramLayout: DiagramLayout | null }) {
    return api<void>(templatePath(business, code), 'PUT', body)
}

export function publishTemplate(business: string, code: string, revision: number) {
    return api<void>(templatePath(business, code) + '/publish', 'POST', { revision })
}

export function listTemplateVersions(business: string, code: string) {
    return api<TemplateVersion[]>(templatePath(business, code) + '/versions')
}

export function getTemplateVersion(business: string, code: string, version: number) {
    return api<TemplateVersionDetail>(templatePath(business, code) + '/versions/' + version)
}

export function restoreTemplateVersion(business: string, code: string, version: number, revision: number) {
    return api<void>(templatePath(business, code) + '/versions/' + version + '/restore', 'POST', { revision })
}

export function setTemplateAvailability(business: string, code: string, revision: number, enabled: boolean) {
    return api<void>(templatePath(business, code) + '/availability', 'PUT', { revision, enabled })
}

export function startTemplateApplication(business: string, code: string,
    body: { businessKey: string; title: string; description: string; amount: string }) {
    return api<{ instanceId: string; templateCode: string; version: number; businessKey: string }>(
        templatePath(business, code) + '/applications', 'POST', body)
}
