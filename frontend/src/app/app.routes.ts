import { Routes } from '@angular/router';
import { authGuard, permissionGuard } from '@core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Acceso · CertiSafe',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    // RF-023: las dos pantallas de recuperacion son publicas, por definicion.
    path: 'recuperar-contrasena',
    title: 'Recuperar contraseña · CertiSafe',
    loadComponent: () =>
      import('./features/auth/password-recovery/password-recovery').then(
        (m) => m.PasswordRecovery,
      ),
  },
  {
    path: 'restablecer-contrasena',
    title: 'Nueva contraseña · CertiSafe',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    // Todo lo que cuelga de aqui exige sesion iniciada.
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'talleres' },
      {
        path: 'talleres',
        title: 'Talleres · CertiSafe',
        canActivate: [permissionGuard('workshops.read')],
        loadComponent: () =>
          import('./features/workshops/workshop-list/workshop-list').then((m) => m.WorkshopList),
      },
      {
        path: 'inscripciones',
        title: 'Inscripciones · CertiSafe',
        canActivate: [permissionGuard('enrollments.manage')],
        loadComponent: () =>
          import('./features/enrollments/enrollment-list/enrollment-list').then(
            (m) => m.EnrollmentList,
          ),
      },
      {
        path: 'certificados',
        title: 'Certificados · CertiSafe',
        canActivate: [permissionGuard('certificates.view')],
        loadComponent: () =>
          import('./features/certificates/certificate-list/certificate-list').then(
            (m) => m.CertificateList,
          ),
      },
      {
        path: 'usuarios',
        title: 'Usuarios · CertiSafe',
        canActivate: [permissionGuard('users.read')],
        loadComponent: () => import('./features/users/user-list/user-list').then((m) => m.UserList),
      },
      {
        path: 'reportes',
        title: 'Reporte de cumplimiento · CertiSafe',
        canActivate: [permissionGuard('reports.view')],
        loadComponent: () =>
          import('./features/reports/compliance-report/compliance-report').then(
            (m) => m.ComplianceReport,
          ),
      },
      {
        path: 'importacion',
        title: 'Importación · CertiSafe',
        canActivate: [permissionGuard('import.execute')],
        loadComponent: () =>
          import('./features/imports/import-page/import-page').then((m) => m.ImportPage),
      },
      {
        path: 'sin-acceso',
        title: 'Sin acceso · CertiSafe',
        loadComponent: () => import('./features/no-access/no-access').then((m) => m.NoAccess),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
