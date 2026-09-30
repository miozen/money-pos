import { createRouter, createWebHashHistory } from 'vue-router'
import intercept from "./interceptor.js"

import Layout from "@/layouts/DashboardLayout.vue"
import NotFound from "@/views/error/NotFound.vue"
import GuestDisplay from "@/views/display/guest/index.vue"

const defaultRouterList = [
    {
        path: '/',
        redirect: () => "/dashboard"
    },
    {
        path: '/guest',
        name: 'GuestDisplay',
        component: GuestDisplay
    },
    {
        path: '/dashboard',
        component: Layout,
        children: [
            {
                path: '',
                name: 'Dashboard',
                component: () => import('@/views/dashboard/index.vue'),
            }
        ]
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/Login.vue'),
    },
    {
        path: '/personal',
        component: Layout,
        children: [
            {
                path: '',
                name: 'Personal',
                component: () => import('@/views/personal/index.vue'),
            }
        ]
    },
    {
        path: '/system/customerDisplay',
        component: Layout,
        children: [
            {
                path: '',
                name: 'CustomerDisplayConfig',
                component: () => import('@/views/system/customerDisplay/index.vue'),
            }
        ]
    },
    {
        path: '/ums/member-benefit',
        component: Layout,
        children: [
            {
                path: '',
                name: 'MemberBenefit',
                component: () => import('@/views/ums/memberBenefit/index.vue'),
            }
        ]
    },
    { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound }
]

export const routes = [...defaultRouterList]

const router = createRouter({
    history: createWebHashHistory(import.meta.env.BASE_URL),
    routes,
})

export default intercept(router)
