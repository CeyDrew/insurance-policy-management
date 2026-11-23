import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ROLE_LABELS, Role } from '../../core/auth/auth.models';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Roles that see the item; empty means every authenticated user. */
  roles: Role[];
}

// Further menu items are added as feature modules land (customers, policies, claims, batch...).
const NAV_ITEMS: NavItem[] = [
  { label: 'Gösterge Paneli', icon: 'dashboard', route: '/dashboard', roles: [] },
  { label: 'Kullanıcılar', icon: 'manage_accounts', route: '/users', roles: ['ADMIN'] },
];

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  protected readonly user = this.auth.currentUser;
  protected readonly roleLabel = computed(() => {
    const user = this.user();
    return user ? ROLE_LABELS[user.role] : '';
  });
  // hasRole() reads the user signal, so the menu updates when the user changes.
  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => item.roles.length === 0 || this.auth.hasRole(...item.roles)),
  );

  protected logout(): void {
    this.auth.logout();
  }
}
