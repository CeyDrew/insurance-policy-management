import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { catchError, map, of, startWith } from 'rxjs';
import { ROLE_LABELS, Role, User } from '../../core/auth/auth.models';
import { errorMessage } from '../../core/http/problem-detail';
import { UserService } from './user.service';

type UsersState =
  | { status: 'loading' }
  | { status: 'loaded'; users: User[] }
  | { status: 'error'; message: string };

@Component({
  selector: 'app-user-list',
  imports: [MatCardModule, MatChipsModule, MatTableModule],
  template: `
    <h1>Kullanıcılar</h1>
    <mat-card appearance="outlined">
      @switch (state().status) {
        @case ('loading') {
          <mat-card-content>Yükleniyor…</mat-card-content>
        }
        @case ('error') {
          <mat-card-content class="error">{{ errorText() }}</mat-card-content>
        }
        @case ('loaded') {
          <table mat-table [dataSource]="users()">
            <ng-container matColumnDef="username">
              <th mat-header-cell *matHeaderCellDef>Kullanıcı adı</th>
              <td mat-cell *matCellDef="let user">{{ user.username }}</td>
            </ng-container>
            <ng-container matColumnDef="fullName">
              <th mat-header-cell *matHeaderCellDef>Ad soyad</th>
              <td mat-cell *matCellDef="let user">{{ user.fullName }}</td>
            </ng-container>
            <ng-container matColumnDef="email">
              <th mat-header-cell *matHeaderCellDef>E-posta</th>
              <td mat-cell *matCellDef="let user">{{ user.email }}</td>
            </ng-container>
            <ng-container matColumnDef="role">
              <th mat-header-cell *matHeaderCellDef>Rol</th>
              <td mat-cell *matCellDef="let user">
                <mat-chip-set
                  ><mat-chip>{{ roleLabel(user.role) }}</mat-chip></mat-chip-set
                >
              </td>
            </ng-container>
            <ng-container matColumnDef="active">
              <th mat-header-cell *matHeaderCellDef>Durum</th>
              <td mat-cell *matCellDef="let user">{{ user.active ? 'Aktif' : 'Pasif' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        }
      }
    </mat-card>
  `,
  styles: `
    .error {
      color: var(--mat-sys-error);
      padding: 16px;
    }
    table {
      width: 100%;
    }
  `,
})
export class UserList {
  protected readonly columns = ['username', 'fullName', 'email', 'role', 'active'];

  protected readonly state = toSignal(
    inject(UserService)
      .findAll()
      .pipe(
        map((users): UsersState => ({ status: 'loaded', users })),
        catchError((err: unknown) =>
          of<UsersState>({ status: 'error', message: errorMessage(err) }),
        ),
        startWith<UsersState>({ status: 'loading' }),
      ),
    { requireSync: true },
  );

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  protected users(): User[] {
    const state = this.state();
    return state.status === 'loaded' ? state.users : [];
  }

  protected errorText(): string {
    const state = this.state();
    return state.status === 'error' ? state.message : '';
  }
}
